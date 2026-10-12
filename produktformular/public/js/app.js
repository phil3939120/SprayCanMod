/* Produktformular – Browser-Logik */
(function () {
  'use strict';

  var S = window.FormSchema;
  var esc = window.Summary.esc;
  var STORAGE_KEY = 'produktformular:v1';
  var CREDENTIAL_HINT = /(passwort|password|kennwort|passwd|\bpw\b|\bpin\b)\s*[:=]/i;

  var state;              // wird im Browser zwischengespeichert (ohne Zugangsdaten)
  var secrets = {};       // Zugangsdaten: nur im Arbeitsspeicher, nie im localStorage
  var errors = {};        // "<produkt>.<abschnitt>.<feld>" -> Meldung
  var uploads = {};       // laufende Uploads je Produkt
  var serverFiles = {};   // Datei-ID -> Metadaten
  var draft = null;
  var saveTimer = null;
  var statusFrame = null;

  var $ = function (sel, root) { return (root || document).querySelector(sel); };
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

  /* ---------------- Zustand ---------------- */
  function newId() {
    return 'p' + Math.random().toString(36).slice(2, 9) + Date.now().toString(36).slice(-4);
  }

  function blankState() {
    return { version: 1, draftId: null, products: [], answers: {}, finalComment: '', open: { vat: true }, submitted: null };
  }

  function addProduct() {
    var id = newId();
    state.products.push({ id: id, name: '', articleNumber: '' });
    state.answers[id] = S.emptyAnswers();
    return id;
  }

  function productIndex(pid) {
    for (var i = 0; i < state.products.length; i++) if (state.products[i].id === pid) return i;
    return -1;
  }

  function loadLocal() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      var s = JSON.parse(raw);
      if (!s || s.version !== 1) return null;
      var n = S.normalize(s);
      // Eingaben exakt wie getippt behalten (normalize kuerzt Leerzeichen)
      s.products.forEach(function (p) {
        if (!n.answers[p.id]) return;
        var src = s.answers[p.id] || {};
        ['vat', 'delivery', 'category', 'supplier', 'price', 'media'].forEach(function (k) {
          var t = n.answers[p.id][k], o = src[k] || {};
          Object.keys(t).forEach(function (f) {
            if (typeof t[f] === 'string' && typeof o[f] === 'string') t[f] = o[f];
          });
        });
      });
      return {
        version: 1,
        draftId: typeof s.draftId === 'string' ? s.draftId : null,
        products: n.products,
        answers: n.answers,
        finalComment: typeof s.finalComment === 'string' ? s.finalComment : '',
        open: s.open && typeof s.open === 'object' ? s.open : { vat: true },
        submitted: s.submitted || null
      };
    } catch (e) {
      return null;
    }
  }

  function save(immediate) {
    clearTimeout(saveTimer);
    var el = $('#save-status');
    el.classList.add('is-saving');
    el.textContent = 'Wird gespeichert …';
    var run = function () {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        el.classList.remove('is-saving');
        el.textContent = 'Entwurf gespeichert · ' + new Date().toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
      } catch (e) {
        el.textContent = 'Zwischenspeichern nicht möglich';
      }
    };
    if (immediate) run();
    else saveTimer = setTimeout(run, 350);
  }

  /* ---------------- Server ---------------- */
  function api(method, url, body) {
    return fetch(url, {
      method: method,
      credentials: 'same-origin',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) {
          var e = new Error(data.error || 'Fehler ' + res.status);
          e.status = res.status;
          e.data = data;
          throw e;
        }
        return data;
      });
    });
  }

  function fileUrl(id, download) {
    return '/api/files/' + encodeURIComponent(id) + (download ? '?download=1' : '');
  }

  // Abgleich der zwischengespeicherten Dateiliste mit dem Server
  function reconcileFiles(files) {
    serverFiles = {};
    files.forEach(function (f) { serverFiles[f.id] = f; });
    var referenced = {};
    state.products.forEach(function (p) {
      var m = state.answers[p.id].media;
      m.files = m.files.filter(function (id) {
        if (!serverFiles[id] || referenced[id]) return false;
        referenced[id] = true;
        return true;
      });
    });
    // Dateien, die hochgeladen wurden, bevor der Entwurf gespeichert war
    files.forEach(function (f) {
      if (!referenced[f.id] && state.answers[f.productId]) state.answers[f.productId].media.files.push(f.id);
    });
  }

  /* ---------------- Hilfen fuer HTML ---------------- */
  function icon(name) {
    var paths = {
      chevron: '<path d="M6 9l6 6 6-6"/>',
      check: '<path d="M5 12l5 5L20 7"/>',
      trash: '<path d="M4 7h16M10 11v6M14 11v6M5 7l1 12a2 2 0 002 2h8a2 2 0 002-2l1-12M9 7V4h6v3"/>',
      upload: '<path d="M12 16V4M7 9l5-5 5 5M4 16v3a2 2 0 002 2h12a2 2 0 002-2v-3"/>',
      lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 018 0v4"/>',
      eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
      x: '<path d="M6 6l12 12M18 6L6 18"/>'
    };
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + paths[name] + '</svg>';
  }

  function errorHtml(key) {
    return errors[key] ? '<div class="field-error" id="err-' + esc(key.replace(/\./g, '-')) + '">' + esc(errors[key]) + '</div>' : '';
  }

  function describedBy(key) {
    return errors[key] ? ' aria-invalid="true" aria-describedby="err-' + esc(key.replace(/\./g, '-')) + '"' : '';
  }

  function field(o) {
    var key = o.errorKey;
    return '<div class="field' + (key && errors[key] ? ' is-invalid' : '') + '"' + (key ? ' data-err="' + esc(key) + '"' : '') + '>' +
      (o.label ? '<label for="' + o.id + '">' + esc(o.label) + (o.required ? '<span class="req" aria-hidden="true">*</span>' : '') +
        (o.optional ? ' <span class="opt">(optional)</span>' : '') + '</label>' : '') +
      o.control + (key ? errorHtml(key) : '') + (o.help ? '<div class="field-help">' + o.help + '</div>' : '') + '</div>';
  }

  function textInput(id, bind, value, attrs) {
    return '<input class="input" type="text" id="' + id + '" data-bind="' + bind + '" value="' + esc(value) + '"' + (attrs || '') + '>';
  }

  function commentField(id, bind, value, label, placeholder) {
    var warn = CREDENTIAL_HINT.test(value);
    return field({
      id: id,
      label: label || 'Kommentar',
      optional: true,
      control: '<textarea class="textarea" id="' + id + '" data-bind="' + bind + '" data-check-credentials placeholder="' + esc(placeholder || 'Ergänzende Informationen …') + '">' + esc(value) + '</textarea>' +
        '<div class="field-warn" data-warn-for="' + id + '"' + (warn ? '' : ' hidden') + '>Bitte keine Passwörter in Kommentare schreiben – nutzen Sie dafür in Abschnitt 4 die verschlüsselte Hinterlegung.</div>'
    });
  }

  function yesNo(pid, section, label, value) {
    var key = pid + '.' + section + '.value';
    var name = section + '-' + pid;
    var opt = function (v, text) {
      return '<label><input type="radio" name="' + name + '" id="' + name + '-' + v + '" value="' + v + '" data-bind="answers.' + pid + '.' + section + '.value"' +
        (value === v ? ' checked' : '') + describedBy(key) + '><span class="dot"></span>' + text + '</label>';
    };
    return '<div class="field' + (errors[key] ? ' is-invalid' : '') + '" data-err="' + key + '">' +
      '<span class="field-label" id="lbl-' + name + '">' + esc(label) + '<span class="req" aria-hidden="true">*</span></span>' +
      '<div class="choice" role="radiogroup" aria-labelledby="lbl-' + name + '">' + opt('ja', 'Ja') + opt('nein', 'Nein') + '</div>' +
      errorHtml(key) + '</div>';
  }

  function linkList(pid, section, list, label, placeholder) {
    var items = list.length ? list : [''];
    var rows = items.map(function (v, i) {
      var key = pid + '.' + section + '.links.' + i;
      var id = section + '-' + pid + '-link-' + i;
      return '<div class="link-row"><div class="field-wrap' + (errors[key] ? ' is-invalid' : '') + '" data-err="' + key + '">' +
        '<label class="sr-only" for="' + id + '">' + esc(label) + ' ' + (i + 1) + '</label>' +
        '<input class="input" type="url" inputmode="url" id="' + id + '" data-bind="answers.' + pid + '.' + section + '.links.' + i + '" value="' + esc(v) + '" placeholder="' + esc(placeholder) + '"' + describedBy(key) + '>' +
        errorHtml(key) + '</div>' +
        (items.length > 1 ? '<button type="button" class="btn-icon" data-action="remove-link" data-pid="' + pid + '" data-list="' + section + '" data-index="' + i + '" title="Link entfernen" aria-label="Link ' + (i + 1) + ' entfernen">' + icon('x') + '</button>' : '') +
        '</div>';
    }).join('');
    return '<div class="field"><span class="field-label">' + esc(label) + ' <span class="opt">(optional)</span></span><div class="link-list">' + rows + '</div>' +
      '<button type="button" class="btn-link" data-action="add-link" data-pid="' + pid + '" data-list="' + section + '">+ Weiteren Link hinzufügen</button></div>';
  }

  /* ---------------- Abschnittsinhalte ---------------- */
  function sectionFields(key, p) {
    var pid = p.id;
    var a = state.answers[pid];
    var base = 'answers.' + pid + '.' + key;
    var id = function (f) { return key + '-' + pid + '-' + f; };

    switch (key) {
      case 'vat':
        return yesNo(pid, 'vat', 'Ist dieses Produkt mehrwertsteuerfrei?', a.vat.value) +
          commentField(id('comment'), base + '.comment', a.vat.comment);

      case 'delivery':
        return yesNo(pid, 'delivery', 'Soll die Lieferwartezeit verlängert werden?', a.delivery.value) +
          (a.delivery.value === 'ja' ? field({
            id: id('days'), label: 'Um wie viele Tage soll die Lieferwartezeit verlängert werden?', required: true, errorKey: pid + '.delivery.days',
            control: '<div class="input-affix input-narrow"><input class="input" type="number" min="1" max="3650" step="1" inputmode="numeric" id="' + id('days') + '" data-bind="' + base + '.days" value="' + esc(a.delivery.days) + '" placeholder="z. B. 7"' + describedBy(pid + '.delivery.days') + '><span class="affix">Tage</span></div>'
          }) : '') +
          commentField(id('comment'), base + '.comment', a.delivery.comment);

      case 'category':
        return field({
          id: id('value'), label: 'Gewünschte Produktkategorie', required: true, errorKey: pid + '.category.value',
          control: textInput(id('value'), base + '.value', a.category.value, ' list="category-options" placeholder="z. B. Haushalt › Küche › Kochgeschirr"' + describedBy(pid + '.category.value'))
        }) + commentField(id('comment'), base + '.comment', a.category.comment, 'Zusätzliche Hinweise');

      case 'supplier':
        var access = Object.keys(S.ACCESS_MODES).map(function (mode) {
          var desc = {
            none: 'Die Lieferantenwebsite ist ohne Anmeldung nutzbar.',
            share: 'Z. B. eigener Gastzugang oder Freigabe über einen Passwort-Manager. Es werden keine Passwörter übermittelt.',
            store: 'Benutzername und Passwort werden verschlüsselt gespeichert und erscheinen weder im PDF noch in Exporten.'
          }[mode];
          return '<label><input type="radio" name="access-' + pid + '" id="' + id('access-' + mode) + '" value="' + mode + '" data-bind="' + base + '.access"' + (a.supplier.access === mode ? ' checked' : '') + '>' +
            '<span><strong>' + esc(S.ACCESS_MODES[mode]) + '</strong><small>' + esc(desc) + '</small></span></label>';
        }).join('');
        var sec = secrets[pid] || {};
        var credKey = pid + '.supplier.credentials';
        var extra = '';
        if (a.supplier.access === 'share') {
          extra = field({
            id: id('share'), label: 'Wie und an wen wird der Zugang freigegeben?', optional: true,
            control: '<textarea class="textarea" id="' + id('share') + '" data-bind="' + base + '.shareNote" data-check-credentials placeholder="z. B. Freigabe per Passwort-Manager an das Team, Gastzugang wird separat eingerichtet …">' + esc(a.supplier.shareNote) + '</textarea>' +
              '<div class="field-warn" data-warn-for="' + id('share') + '"' + (CREDENTIAL_HINT.test(a.supplier.shareNote) ? '' : ' hidden') + '>Bitte hier keine Passwörter eintragen.</div>'
          });
        } else if (a.supplier.access === 'store') {
          extra = '<div class="secure-box' + (errors[credKey] ? ' is-invalid' : '') + '" data-err="' + credKey + '">' +
            '<div class="secure-box-head">' + icon('lock') + '<span>Verschlüsselte Übertragung und Speicherung (AES-256). Aus Sicherheitsgründen werden diese Felder nicht zwischengespeichert.</span></div>' +
            '<div class="grid-2">' +
            field({ id: id('cred-username'), label: 'Benutzername / E-Mail', control: '<input class="input" type="text" id="' + id('cred-username') + '" data-secret="' + pid + '.username" value="' + esc(sec.username || '') + '" autocomplete="off" spellcheck="false">' }) +
            field({ id: id('cred-password'), label: 'Passwort', control: '<div class="input-affix"><input class="input" type="password" id="' + id('cred-password') + '" data-secret="' + pid + '.password" value="' + esc(sec.password || '') + '" autocomplete="new-password" spellcheck="false">' +
              '<button type="button" class="pw-toggle btn-ghost" data-action="toggle-password" data-target="' + id('cred-password') + '" aria-label="Passwort anzeigen">' + icon('eye') + '</button></div>' }) +
            '</div>' +
            field({ id: id('cred-note'), label: 'Weitere Zugangsinformationen', optional: true, control: '<input class="input" type="text" id="' + id('cred-note') + '" data-secret="' + pid + '.note" value="' + esc(sec.note || '') + '" placeholder="z. B. Kundennummer, Login-Adresse" autocomplete="off">' }) +
            errorHtml(credKey) + '</div>';
        }
        return field({
          id: id('name'), label: 'Name des Lieferanten', required: true, errorKey: pid + '.supplier.name',
          control: textInput(id('name'), base + '.name', a.supplier.name, ' placeholder="z. B. Muster Großhandel GmbH" list="supplier-options"' + describedBy(pid + '.supplier.name'))
        }) +
          linkList(pid, 'supplier', a.supplier.links, 'Link zur Lieferantenwebsite', 'https://www.lieferant.de/produkt') +
          '<div class="field"><span class="field-label">Zugangsinformationen</span><div class="access-options">' + access + '</div></div>' +
          extra +
          commentField(id('comment'), base + '.comment', a.supplier.comment);

      case 'price':
        return field({
          id: id('value'), label: 'Gewünschter Verkaufspreis (brutto)', required: true, errorKey: pid + '.price.value',
          control: '<div class="input-affix input-narrow"><input class="input" type="text" inputmode="decimal" id="' + id('value') + '" data-bind="' + base + '.value" value="' + esc(a.price.value) + '" placeholder="0,00"' + describedBy(pid + '.price.value') + '><span class="affix">€</span></div>'
        }) + commentField(id('comment'), base + '.comment', a.price.comment, 'Informationen zur Preisgestaltung', 'z. B. Staffelpreise, Aktionspreis, UVP …');

      case 'media':
        var accept = Object.keys(S.FILE_TYPES).map(function (e) { return '.' + e; }).join(',');
        return commentField(id('comment'), base + '.comment', a.media.comment, 'Hinweise zu den Medien', 'z. B. welches Bild als Hauptbild verwendet werden soll …') +
          linkList(pid, 'media', a.media.links, 'Links zu Bildern, Videos oder anderen Medien', 'https://…') +
          '<div class="field"><span class="field-label">Dateien hochladen <span class="opt">(optional)</span></span>' +
          '<div class="dropzone" data-dropzone data-pid="' + pid + '">' +
          '<div class="dropzone-icon">' + icon('upload').replace('width="18" height="18"', 'width="40" height="40"') + '</div>' +
          '<p><strong>Dateien hierher ziehen</strong> oder</p>' +
          '<button type="button" class="btn btn-secondary" data-action="pick-files" data-pid="' + pid + '">Datei auswählen</button>' +
          '<input type="file" multiple hidden id="' + id('files') + '" data-file-input data-pid="' + pid + '" accept="' + accept + '">' +
          '<small>JPG, PNG, WEBP, GIF, PDF, DOCX, DOC, XLSX, PPTX, ODT, TXT, CSV, MP4, MOV, ZIP · max. ' + S.formatBytes(S.LIMITS.maxFileBytes) + ' pro Datei · mehrere Dateien gleichzeitig möglich</small>' +
          '</div><ul class="file-list" id="files-' + pid + '" aria-live="polite">' + fileItems(pid) + '</ul></div>';
    }
    return '';
  }

  function fileItems(pid) {
    var html = state.answers[pid].media.files.map(function (fid) {
      var f = serverFiles[fid];
      if (!f) return '';
      var thumb = f.image
        ? '<img src="' + esc(fileUrl(f.id)) + '" alt="Vorschau ' + esc(f.name) + '" loading="lazy">'
        : esc(S.extOf(f.name));
      return '<li class="file-item"><span class="file-thumb">' + thumb + '</span>' +
        '<span class="file-info"><a class="file-name" href="' + esc(fileUrl(f.id, !f.image)) + '" target="_blank" rel="noopener">' + esc(f.name) + '</a>' +
        '<span class="file-meta">' + esc(S.formatBytes(f.size)) + ' · hochgeladen</span></span>' +
        '<button type="button" class="btn-icon" data-action="remove-file" data-pid="' + pid + '" data-file-id="' + esc(f.id) + '" aria-label="' + esc(f.name) + ' löschen" title="Datei löschen">' + icon('trash') + '</button></li>';
    }).join('');
    (uploads[pid] || []).forEach(function (u) {
      html += '<li class="file-item' + (u.error ? ' is-error' : '') + '"><span class="file-thumb">' + (u.preview ? '<img src="' + esc(u.preview) + '" alt="">' : esc(S.extOf(u.name))) + '</span>' +
        '<span class="file-info"><span class="file-name">' + esc(u.name) + '</span>' +
        '<span class="file-meta">' + (u.error ? esc(u.error) : esc(S.formatBytes(u.size)) + ' · wird hochgeladen …') + '</span>' +
        (u.error ? '' : '<div class="file-progress"><div id="up-' + u.tmpId + '"></div></div>') + '</span>' +
        (u.error ? '<button type="button" class="btn-icon" data-action="dismiss-upload" data-pid="' + pid + '" data-tmp="' + u.tmpId + '" aria-label="Meldung schließen">' + icon('x') + '</button>' : '<span></span>') +
        '</li>';
    });
    return html;
  }

  function refreshFiles(pid) {
    var ul = document.getElementById('files-' + pid);
    if (!ul) return;
    ul.innerHTML = fileItems(pid);
    (uploads[pid] || []).forEach(function (u) {
      var bar = document.getElementById('up-' + u.tmpId);
      if (bar) bar.style.width = u.progress + '%';
    });
  }

  /* ---------------- Darstellung ---------------- */
  function sectionInfo(key) {
    var complete = 0, hasError = false;
    state.products.forEach(function (p) {
      if (S.sectionStatus(key, state.answers[p.id]) === 'complete') complete++;
      Object.keys(errors).forEach(function (k) { if (k.indexOf(p.id + '.' + key + '.') === 0) hasError = true; });
    });
    var total = state.products.length;
    return { complete: complete, total: total, done: total > 0 && complete === total, error: hasError, partial: complete > 0 };
  }

  function sectionSubtitle(info, key) {
    if (info.error) return 'Bitte markierte Angaben prüfen';
    if (!info.total) return 'Noch keine Produkte';
    var t = info.complete + ' von ' + info.total + (info.total === 1 ? ' Produkt' : ' Produkten') + (key === 'media' ? ' mit Medien' : ' vollständig');
    return key === 'media' ? t + ' · optional' : t;
  }

  function renderProgress() {
    var infos = S.SECTIONS.map(function (s) { return sectionInfo(s.key); });
    var done = infos.filter(function (i) { return i.done; }).length;
    var n = state.products.length;
    $('#progress').innerHTML =
      '<div class="progress-head"><strong>' + done + ' von 6 Abschnitten vollständig</strong><span>' + n + (n === 1 ? ' Produkt' : ' Produkte') + '</span></div>' +
      '<div class="progress-bar" role="progressbar" aria-valuemin="0" aria-valuemax="6" aria-valuenow="' + done + '" aria-label="Fortschritt"><div></div></div>' +
      '<ol class="steps">' + S.SECTIONS.map(function (s, i) {
        var info = infos[i];
        var cls = info.error ? 'is-error' : info.done ? 'is-complete' : info.partial ? 'is-partial' : '';
        return '<li><button type="button" class="step ' + cls + '" data-action="goto" data-section="' + s.key + '" title="' + esc(s.title) + '">' +
          '<span class="step-nr">' + (info.done && !info.error ? '✓' : s.nr) + '</span><span class="step-label">' + esc(s.short) + '</span></button></li>';
      }).join('') + '</ol>';
    $('#progress .progress-bar > div').style.width = (done / 6 * 100) + '%';
    infos.forEach(function (info, i) {
      var key = S.SECTIONS[i].key;
      var el = document.getElementById('sec-' + key);
      if (!el) return;
      el.classList.toggle('is-complete', info.done && !info.error);
      el.classList.toggle('is-error', info.error);
      $('.section-nr', el).textContent = info.done && !info.error ? '✓' : S.SECTIONS[i].nr;
      $('.section-sub', el).textContent = sectionSubtitle(info, key);
    });
  }

  function scheduleStatus() {
    if (statusFrame) return;
    statusFrame = requestAnimationFrame(function () {
      statusFrame = null;
      renderProgress();
    });
  }

  function datalist(id, values) {
    var seen = {};
    return '<datalist id="' + id + '">' + values.filter(function (v) {
      v = v.trim();
      if (!v || seen[v]) return false;
      seen[v] = true;
      return true;
    }).map(function (v) { return '<option value="' + esc(v.trim()) + '"></option>'; }).join('') + '</datalist>';
  }

  function renderSections() {
    var html = datalist('category-options', state.products.map(function (p) { return state.answers[p.id].category.value; })) +
      datalist('supplier-options', state.products.map(function (p) { return state.answers[p.id].supplier.name; }));
    S.SECTIONS.forEach(function (s) {
      var open = !!state.open[s.key];
      var info = sectionInfo(s.key);
      html += '<section class="section' + (open ? ' is-open' : '') + '" id="sec-' + s.key + '">' +
        '<h2 class="section-heading"><button type="button" class="section-toggle" data-action="toggle" data-section="' + s.key + '" aria-expanded="' + open + '" aria-controls="body-' + s.key + '">' +
        '<span class="section-nr">' + s.nr + '</span>' +
        '<span class="section-title"><span class="section-title-text">Abschnitt ' + s.nr + ': ' + esc(s.title) + '</span>' +
        '<small class="section-sub">' + esc(sectionSubtitle(info, s.key)) + '</small></span>' +
        '<span class="chevron">' + icon('chevron') + '</span></button></h2>' +
        '<div class="section-body" id="body-' + s.key + '"' + (open ? '' : ' hidden') + '>';
      if (!state.products.length) html += '<p class="hint">Noch keine Produkte vorhanden. Fügen Sie Ihr erstes Produkt hinzu.</p>';
      state.products.forEach(function (p, i) {
        var hasErr = Object.keys(errors).some(function (k) { return k.indexOf(p.id + '.' + s.key + '.') === 0; });
        html += '<div class="product-card' + (hasErr ? ' has-error' : '') + '" id="card-' + s.key + '-' + p.id + '">' +
          '<div class="product-head"><span class="product-badge">Produkt ' + (i + 1) + '</span>' +
          '<span class="product-name-display' + (p.name ? '' : ' is-empty') + '" data-name-display="' + p.id + '">' + esc(p.name || 'Noch ohne Namen') + '</span>' +
          '<button type="button" class="btn-danger-ghost" data-action="remove-product" data-pid="' + p.id + '">' + icon('trash') + ' Entfernen</button></div>' +
          '<div class="product-ident">' +
          field({ id: s.key + '-' + p.id + '-pname', label: 'Produktname', optional: true, control: textInput(s.key + '-' + p.id + '-pname', 'product.' + p.id + '.name', p.name, ' placeholder="z. B. Edelstahl-Topf 24 cm"') }) +
          field({ id: s.key + '-' + p.id + '-pnr', label: 'Artikelnummer', optional: true, control: textInput(s.key + '-' + p.id + '-pnr', 'product.' + p.id + '.articleNumber', p.articleNumber, ' placeholder="z. B. ART-1001"') }) +
          '</div><div class="product-fields">' + sectionFields(s.key, p) + '</div></div>';
      });
      html += '<button type="button" class="add-product" data-action="add-product" data-section="' + s.key + '"><span class="plus" aria-hidden="true">+</span> Produkt hinzufügen</button>' +
        '</div></section>';
    });
    $('#sections').innerHTML = html;
  }

  // Neu zeichnen und Fokus/Cursor erhalten
  function render() {
    var ae = document.activeElement;
    var focusId = ae && ae.id;
    var sel = null;
    try { if (ae && typeof ae.selectionStart === 'number') sel = [ae.selectionStart, ae.selectionEnd]; } catch (e) {}
    renderSections();
    renderProgress();
    var fc = $('#final-comment');
    if (fc.value !== state.finalComment) fc.value = state.finalComment;
    if (focusId) {
      var el = document.getElementById(focusId);
      if (el) {
        el.focus({ preventScroll: true });
        if (sel) try { el.setSelectionRange(sel[0], sel[1]); } catch (e) {}
      }
    }
  }

  function notice(type, html, id) {
    var box = $('#notices');
    if (id) {
      var old = document.getElementById(id);
      if (old) old.remove();
    }
    var div = document.createElement('div');
    div.className = 'notice notice-' + type;
    if (id) div.id = id;
    div.setAttribute('role', type === 'error' ? 'alert' : 'status');
    div.innerHTML = '<div>' + html + '</div><button type="button" class="btn-ghost" data-action="close-notice" aria-label="Hinweis schließen">' + icon('x') + '</button>';
    box.appendChild(div);
    return div;
  }

  var toastTimer;
  function toast(msg) {
    var t = $('.toast') || document.body.appendChild(Object.assign(document.createElement('div'), { className: 'toast' }));
    t.setAttribute('role', 'status');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 2800);
  }

  /* ---------------- Eingaben ---------------- */
  function clearError(key) {
    if (!errors[key]) return;
    delete errors[key];
    $$('[data-err="' + key + '"]').forEach(function (w) {
      w.classList.remove('is-invalid');
      var e = $('.field-error', w);
      if (e) e.remove();
      $$('[aria-invalid]', w).forEach(function (i) { i.removeAttribute('aria-invalid'); i.removeAttribute('aria-describedby'); });
    });
    $$('.product-card.has-error').forEach(function (card) {
      if (!$('.is-invalid', card)) card.classList.remove('has-error');
    });
  }

  function onBind(el) {
    var path = el.getAttribute('data-bind');
    var value = el.value;
    var parts = path.split('.');
    if (parts[0] === 'finalComment') {
      state.finalComment = value;
    } else if (parts[0] === 'product') {
      var p = state.products[productIndex(parts[1])];
      if (!p) return;
      p[parts[2]] = value;
      $$('[data-bind="' + path + '"]').forEach(function (other) { if (other !== el) other.value = value; });
      if (parts[2] === 'name') {
        $$('[data-name-display="' + p.id + '"]').forEach(function (d) {
          d.textContent = value.trim() || 'Noch ohne Namen';
          d.classList.toggle('is-empty', !value.trim());
        });
      }
    } else if (parts[0] === 'answers') {
      var pid = parts[1], section = parts[2], fieldName = parts[3];
      var target = state.answers[pid] && state.answers[pid][section];
      if (!target) return;
      if (parts.length === 5) {
        target[fieldName][Number(parts[4])] = value;
      } else {
        target[fieldName] = value;
      }
      clearError(parts.slice(1).join('.'));
      // Felder, die weitere Eingaben ein- oder ausblenden
      if ((section === 'delivery' && fieldName === 'value') || (section === 'supplier' && fieldName === 'access')) {
        if (section === 'delivery' && value === 'nein') clearError(pid + '.delivery.days');
        if (section === 'supplier') clearError(pid + '.supplier.credentials');
        render();
      }
    }
    if (el.hasAttribute('data-check-credentials')) {
      var w = $('[data-warn-for="' + el.id + '"]');
      if (w) w.hidden = !CREDENTIAL_HINT.test(value);
    }
    save();
    scheduleStatus();
  }

  function onSecret(el) {
    var parts = el.getAttribute('data-secret').split('.');
    secrets[parts[0]] = secrets[parts[0]] || {};
    secrets[parts[0]][parts[1]] = el.value;
    clearError(parts[0] + '.supplier.credentials');
    scheduleStatus();
  }

  /* ---------------- Produkte ---------------- */
  function onAddProduct(sectionKey) {
    var pid = addProduct();
    state.open[sectionKey] = true;
    save();
    render();
    var card = document.getElementById('card-' + sectionKey + '-' + pid);
    if (card) {
      card.scrollIntoView({ behavior: 'smooth', block: 'center' });
      var input = $('input', card);
      if (input) input.focus({ preventScroll: true });
    }
    toast('Produkt ' + state.products.length + ' hinzugefügt – in allen sechs Abschnitten verfügbar.');
  }

  function onRemoveProduct(pid) {
    var i = productIndex(pid);
    if (i < 0) return;
    var p = state.products[i];
    var files = state.answers[pid].media.files.length;
    var msg = 'Produkt ' + (i + 1) + (p.name ? ' „' + p.name + '“' : '') + ' wirklich entfernen?\n\nAlle Angaben zu diesem Produkt werden in allen sechs Abschnitten gelöscht' +
      (files ? ', einschließlich ' + files + (files === 1 ? ' hochgeladener Datei' : ' hochgeladener Dateien') : '') + '.';
    if (!window.confirm(msg)) return;
    state.answers[pid].media.files.forEach(function (fid) {
      api('DELETE', fileUrl(fid)).catch(function () {});
      delete serverFiles[fid];
    });
    state.products.splice(i, 1);
    delete state.answers[pid];
    delete secrets[pid];
    delete uploads[pid];
    Object.keys(errors).forEach(function (k) { if (k.indexOf(pid + '.') === 0) delete errors[k]; });
    save();
    render();
    toast('Produkt entfernt. Die Nummerierung wurde angepasst.');
  }

  /* ---------------- Dateien ---------------- */
  var tmpCounter = 0;
  function uploadFiles(pid, fileList) {
    Array.prototype.forEach.call(fileList, function (file) { uploadFile(pid, file); });
  }

  function uploadFile(pid, file) {
    uploads[pid] = uploads[pid] || [];
    var ext = S.extOf(file.name);
    var type = S.FILE_TYPES[ext];
    var entry = { tmpId: 'u' + (++tmpCounter), name: file.name, size: file.size, progress: 0, error: null, preview: null };
    uploads[pid].push(entry);
    if (!type) entry.error = 'Dateityp „.' + (ext || '?') + '“ wird nicht unterstützt.';
    else if (file.size > S.LIMITS.maxFileBytes) entry.error = 'Datei ist größer als ' + S.formatBytes(S.LIMITS.maxFileBytes) + '.';
    else if (file.size === 0) entry.error = 'Die Datei ist leer.';
    if (!entry.error && type.image) entry.preview = URL.createObjectURL(file);
    refreshFiles(pid);
    if (entry.error) return;

    var finish = function () {
      if (entry.preview) URL.revokeObjectURL(entry.preview);
      entry.preview = null;
    };
    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/files');
    xhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));
    xhr.setRequestHeader('X-Product-Id', pid);
    xhr.setRequestHeader('Content-Type', type.mime);
    xhr.upload.onprogress = function (e) {
      if (!e.lengthComputable) return;
      entry.progress = Math.round((e.loaded / e.total) * 100);
      var bar = document.getElementById('up-' + entry.tmpId);
      if (bar) bar.style.width = entry.progress + '%';
    };
    xhr.onload = function () {
      var data = {};
      try { data = JSON.parse(xhr.responseText); } catch (e) {}
      finish();
      if (xhr.status === 201 && data.file) {
        var list = uploads[pid] || [];
        list.splice(list.indexOf(entry), 1);
        serverFiles[data.file.id] = data.file;
        if (state.answers[pid]) {
          state.answers[pid].media.files.push(data.file.id);
          save();
        } else {
          api('DELETE', fileUrl(data.file.id)).catch(function () {});
        }
      } else {
        entry.error = data.error || (xhr.status === 401 ? 'Sitzung abgelaufen – bitte Seite neu laden.' : 'Upload fehlgeschlagen.');
      }
      refreshFiles(pid);
      scheduleStatus();
    };
    xhr.onerror = function () {
      finish();
      entry.error = 'Upload fehlgeschlagen. Bitte Verbindung prüfen und erneut versuchen.';
      refreshFiles(pid);
    };
    xhr.send(file);
  }

  function pendingUploads() {
    return Object.keys(uploads).reduce(function (n, pid) {
      return n + uploads[pid].filter(function (u) { return !u.error; }).length;
    }, 0);
  }

  function onRemoveFile(pid, fid) {
    var f = serverFiles[fid];
    if (!window.confirm('Datei „' + (f ? f.name : '') + '“ löschen?')) return;
    api('DELETE', fileUrl(fid)).then(function () {
      var m = state.answers[pid].media;
      m.files = m.files.filter(function (x) { return x !== fid; });
      delete serverFiles[fid];
      save();
      refreshFiles(pid);
      scheduleStatus();
    }).catch(function (e) {
      toast(e.message);
    });
  }

  /* ---------------- Absenden ---------------- */
  function collect() {
    return S.normalize(state);
  }

  function applyErrors(list) {
    errors = {};
    list.forEach(function (e) {
      if (e.productId) errors[e.productId + '.' + e.section + '.' + e.field] = e.message;
    });
    S.SECTIONS.forEach(function (s) {
      if (list.some(function (e) { return e.section === s.key; })) state.open[s.key] = true;
    });
    render();
    var first = $('.is-invalid');
    var n = Object.keys(errors).length;
    notice('error', '<strong>' + (n === 1 ? 'Eine Angabe fehlt oder ist ungültig.' : n + ' Angaben fehlen oder sind ungültig.') + '</strong> Die betroffenen Felder sind rot markiert.', 'validation-notice');
    if (first) {
      first.scrollIntoView({ behavior: 'smooth', block: 'center' });
      var input = $('input, textarea', first);
      if (input) input.focus({ preventScroll: true });
    }
  }

  function onSubmit() {
    if (pendingUploads()) {
      notice('warn', 'Bitte warten Sie, bis alle Dateien hochgeladen sind.', 'upload-notice');
      return;
    }
    var data = collect();
    var list = S.validate(data, secrets);
    if (list.length && !list[0].productId) {
      notice('error', esc(list[0].message), 'validation-notice');
      return;
    }
    if (list.length) return applyErrors(list);
    var old = document.getElementById('validation-notice');
    if (old) old.remove();
    openReview(data);
  }

  function openReview(data) {
    var root = $('#modal-root');
    root.innerHTML = '<div class="modal-backdrop" data-action="backdrop"><div class="modal" role="dialog" aria-modal="true" aria-labelledby="review-title">' +
      '<div class="modal-head"><h2 id="review-title" tabindex="-1">Angaben überprüfen</h2><p>Bitte prüfen Sie Ihre Angaben. Sie können jederzeit zur Bearbeitung zurückkehren.</p></div>' +
      '<div class="modal-body">' + window.Summary.render(data, {
        files: serverFiles,
        fileUrl: fileUrl,
        accessHtml: function (p, a) {
          if (a.supplier.access !== 'store') return esc(S.ACCESS_MODES[a.supplier.access]);
          return '<span class="tag tag-lock">' + icon('lock') + ' Zugangsdaten werden verschlüsselt übermittelt</span>';
        }
      }) + '</div>' +
      '<div class="modal-foot"><button type="button" class="btn btn-secondary" data-action="close-modal">Zurück zur Bearbeitung</button>' +
      '<button type="button" class="btn btn-primary" data-action="confirm-submit">Jetzt verbindlich absenden</button></div>' +
      '</div></div>';
    document.body.classList.add('modal-open');
    $('#review-title').focus();
  }

  function closeModal() {
    $('#modal-root').innerHTML = '';
    document.body.classList.remove('modal-open');
  }

  function confirmSubmit(btn) {
    btn.disabled = true;
    btn.textContent = 'Wird gesendet …';
    var data = collect();
    var creds = {};
    data.products.forEach(function (p) {
      if (data.answers[p.id].supplier.access === 'store' && secrets[p.id]) creds[p.id] = secrets[p.id];
    });
    api('POST', '/api/submit', { data: data, credentials: creds }).then(function (res) {
      closeModal();
      secrets = {};
      errors = {};
      state.submitted = { reference: res.reference, submittedAt: res.submittedAt };
      save(true);
      showDone();
    }).catch(function (e) {
      closeModal();
      if (e.status === 422 && e.data && e.data.errors) return applyErrors(e.data.errors);
      notice('error', '<strong>Das Formular konnte nicht gesendet werden.</strong> ' + esc(e.message) + ' Ihre Angaben bleiben erhalten – bitte versuchen Sie es erneut.', 'submit-notice');
    });
  }

  function showDone() {
    $('#form-view').hidden = true;
    var v = $('#done-view');
    v.hidden = false;
    var at = state.submitted.submittedAt ? new Date(state.submitted.submittedAt).toLocaleString('de-DE', { dateStyle: 'long', timeStyle: 'short' }) + ' Uhr' : '';
    v.innerHTML = '<div class="done"><div class="done-icon">' + icon('check').replace('width="18" height="18"', 'width="36" height="36"') + '</div>' +
      '<h1 tabindex="-1" id="done-title">Vielen Dank – Ihr Formular wurde gesendet.</h1>' +
      '<p>Alle Angaben und Dateien wurden sicher gespeichert' + (at ? ' (' + esc(at) + ')' : '') + '. Bitte notieren Sie Ihre Referenznummer:</p>' +
      '<div class="reference">' + esc(state.submitted.reference) + '</div>' +
      '<p>Das PDF enthält alle Produkte, Antworten, Kommentare, Links und Dateiverweise – ohne Zugangsdaten.</p>' +
      '<div class="done-actions"><a class="btn btn-primary" href="/api/submission/pdf">PDF herunterladen</a>' +
      '<button type="button" class="btn btn-secondary" data-action="new-form">Neues Formular beginnen</button></div></div>';
    $('#save-status').textContent = 'Abgesendet';
    $('#save-status').classList.remove('is-saving');
    window.scrollTo(0, 0);
    $('#done-title').focus();
  }

  function startNew() {
    if (!window.confirm('Ein neues, leeres Formular beginnen?')) return;
    api('POST', '/api/drafts').then(function (r) {
      draft = r.draft;
      state = blankState();
      state.draftId = draft.id;
      addProduct();
      serverFiles = {};
      uploads = {};
      errors = {};
      secrets = {};
      save(true);
      $('#done-view').hidden = true;
      $('#form-view').hidden = false;
      $('#notices').innerHTML = '';
      render();
      window.scrollTo(0, 0);
    }).catch(function (e) { toast(e.message); });
  }

  /* ---------------- Ereignisse ---------------- */
  function bindEvents() {
    document.addEventListener('input', function (e) {
      var t = e.target;
      if (t.type === 'radio') return;
      if (t.hasAttribute('data-bind')) onBind(t);
      else if (t.hasAttribute('data-secret')) onSecret(t);
    });
    document.addEventListener('change', function (e) {
      var t = e.target;
      if (t.type === 'radio' && t.hasAttribute('data-bind')) onBind(t);
      else if (t.hasAttribute('data-file-input')) {
        uploadFiles(t.getAttribute('data-pid'), t.files);
        t.value = '';
      }
    });

    document.addEventListener('click', function (e) {
      var el = e.target.closest('[data-action]');
      if (!el) return;
      var a = el.getAttribute('data-action');
      var pid = el.getAttribute('data-pid');
      switch (a) {
        case 'toggle':
          var key = el.getAttribute('data-section');
          state.open[key] = !state.open[key];
          el.setAttribute('aria-expanded', String(!!state.open[key]));
          document.getElementById('sec-' + key).classList.toggle('is-open', !!state.open[key]);
          document.getElementById('body-' + key).hidden = !state.open[key];
          save();
          break;
        case 'goto':
          var k = el.getAttribute('data-section');
          if (!state.open[k]) {
            state.open[k] = true;
            render();
            save();
          }
          document.getElementById('sec-' + k).scrollIntoView({ behavior: 'smooth', block: 'start' });
          break;
        case 'add-product':
          onAddProduct(el.getAttribute('data-section'));
          break;
        case 'remove-product':
          onRemoveProduct(pid);
          break;
        case 'add-link':
          var lst = state.answers[pid][el.getAttribute('data-list')].links;
          if (!lst.length) lst.push('');
          if (lst.length >= S.LIMITS.linksPerList) return toast('Maximal ' + S.LIMITS.linksPerList + ' Links möglich.');
          lst.push('');
          save();
          render();
          var newInput = document.getElementById(el.getAttribute('data-list') + '-' + pid + '-link-' + (lst.length - 1));
          if (newInput) newInput.focus();
          break;
        case 'remove-link':
          var listName = el.getAttribute('data-list');
          state.answers[pid][listName].links.splice(Number(el.getAttribute('data-index')), 1);
          Object.keys(errors).forEach(function (ek) { if (ek.indexOf(pid + '.' + listName + '.links.') === 0) delete errors[ek]; });
          save();
          render();
          break;
        case 'pick-files':
          var input = el.parentNode.querySelector('[data-file-input]');
          if (input) input.click();
          break;
        case 'remove-file':
          onRemoveFile(pid, el.getAttribute('data-file-id'));
          break;
        case 'dismiss-upload':
          uploads[pid] = (uploads[pid] || []).filter(function (u) { return u.tmpId !== el.getAttribute('data-tmp'); });
          refreshFiles(pid);
          break;
        case 'toggle-password':
          var pw = document.getElementById(el.getAttribute('data-target'));
          pw.type = pw.type === 'password' ? 'text' : 'password';
          el.setAttribute('aria-label', pw.type === 'password' ? 'Passwort anzeigen' : 'Passwort verbergen');
          break;
        case 'close-notice':
          el.closest('.notice').remove();
          break;
        case 'backdrop':
          if (e.target === el) closeModal();
          break;
        case 'close-modal':
          closeModal();
          break;
        case 'confirm-submit':
          confirmSubmit(el);
          break;
        case 'new-form':
          startNew();
          break;
      }
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && $('#modal-root').innerHTML) closeModal();
    });

    $('#form').addEventListener('submit', function (e) {
      e.preventDefault();
      onSubmit();
    });
    // Enter in einzeiligen Feldern soll nicht absenden
    $('#form').addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && e.target.tagName === 'INPUT' && e.target.type !== 'submit') e.preventDefault();
    });

    // Drag & Drop
    var zoneOf = function (e) { return e.target.closest && e.target.closest('[data-dropzone]'); };
    document.addEventListener('dragover', function (e) {
      e.preventDefault();
      var z = zoneOf(e);
      e.dataTransfer.dropEffect = z ? 'copy' : 'none';
      if (z) z.classList.add('is-dragover');
    });
    document.addEventListener('dragleave', function (e) {
      var z = zoneOf(e);
      if (z && !z.contains(e.relatedTarget)) z.classList.remove('is-dragover');
    });
    document.addEventListener('drop', function (e) {
      e.preventDefault();
      var z = zoneOf(e);
      if (!z) return;
      z.classList.remove('is-dragover');
      if (e.dataTransfer.files && e.dataTransfer.files.length) uploadFiles(z.getAttribute('data-pid'), e.dataTransfer.files);
    });

    window.addEventListener('beforeunload', function (e) {
      save(true);
      if (pendingUploads()) {
        e.preventDefault();
        e.returnValue = '';
      }
    });
  }

  /* ---------------- Start ---------------- */
  function init() {
    state = loadLocal() || blankState();
    bindEvents();
    api('GET', '/api/session').then(function (r) {
      draft = r.draft;
      if (state.submitted && draft && draft.status === 'submitted' && draft.id === state.draftId) {
        showDone();
        return null;
      }
      if (state.submitted) {
        // Abgeschlossenes Formular ohne gueltige Sitzung: Bestaetigung trotzdem zeigen
        showDone();
        return null;
      }
      if (!draft || draft.status !== 'draft') return api('POST', '/api/drafts').then(function (r2) { draft = r2.draft; return draft; });
      return draft;
    }).then(function (d) {
      if (!d) return;
      if (state.draftId && state.draftId !== d.id) {
        var lost = 0;
        state.products.forEach(function (p) { lost += state.answers[p.id].media.files.length; });
        if (lost) notice('warn', 'Ihre Sitzung war abgelaufen. Ihre Texteingaben sind erhalten, bitte laden Sie die Dateien jedoch erneut hoch.');
      }
      state.draftId = d.id;
      reconcileFiles(d.files || []);
      if (!state.products.length) addProduct();
      save(true);
      render();
      if (state.products.some(function (p) { return state.answers[p.id].supplier.access === 'store'; })) {
        notice('info', 'Hinweis: Zugangsdaten werden aus Sicherheitsgründen nicht im Browser zwischengespeichert. Bitte tragen Sie sie in Abschnitt 4 vor dem Absenden erneut ein.', 'cred-notice');
      }
    }).catch(function () {
      if (!state.products.length) addProduct();
      render();
      notice('error', '<strong>Keine Verbindung zum Server.</strong> Ihre Eingaben werden lokal gespeichert. Datei-Uploads und Absenden sind erst wieder möglich, wenn die Verbindung besteht. Bitte laden Sie die Seite später neu.');
    });
  }

  init();
})();
