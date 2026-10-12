/*
 * Gemeinsame Formular-Definition fuer Browser und Server.
 * Enthaelt Abschnitte, Dateiregeln, Normalisierung und Validierung,
 * damit Pflichtangaben auf beiden Seiten identisch geprueft werden.
 */
(function (root) {
  'use strict';

  var SECTIONS = [
    { key: 'vat', nr: 1, title: 'Sind die Produkte mehrwertsteuerfrei?', short: 'Mehrwertsteuer' },
    { key: 'delivery', nr: 2, title: 'Soll die Lieferwartezeit verlängert werden?', short: 'Lieferzeit' },
    { key: 'category', nr: 3, title: 'Unter welcher Kategorie sollen die Produkte eingeordnet werden?', short: 'Kategorie' },
    { key: 'supplier', nr: 4, title: 'Welche Lieferanten sollen verwendet werden?', short: 'Lieferanten' },
    { key: 'price', nr: 5, title: 'Welcher Preis soll verwendet werden?', short: 'Preis' },
    { key: 'media', nr: 6, title: 'Welche Mediendateien sollen verwendet werden?', short: 'Medien' }
  ];

  var ACCESS_MODES = {
    none: 'Keine Zugangsdaten erforderlich',
    share: 'Zugang wird sicher separat freigegeben (empfohlen)',
    store: 'Zugangsdaten verschlüsselt hinterlegen'
  };

  // Erlaubte Dateitypen: Endung -> MIME-Typ, inline = darf im Browser angezeigt werden
  var FILE_TYPES = {
    jpg: { mime: 'image/jpeg', inline: true, image: true },
    jpeg: { mime: 'image/jpeg', inline: true, image: true },
    png: { mime: 'image/png', inline: true, image: true },
    webp: { mime: 'image/webp', inline: true, image: true },
    gif: { mime: 'image/gif', inline: true, image: true },
    pdf: { mime: 'application/pdf', inline: false },
    docx: { mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', inline: false },
    doc: { mime: 'application/msword', inline: false },
    xlsx: { mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', inline: false },
    xls: { mime: 'application/vnd.ms-excel', inline: false },
    pptx: { mime: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', inline: false },
    odt: { mime: 'application/vnd.oasis.opendocument.text', inline: false },
    ods: { mime: 'application/vnd.oasis.opendocument.spreadsheet', inline: false },
    txt: { mime: 'text/plain', inline: false },
    csv: { mime: 'text/csv', inline: false },
    mp4: { mime: 'video/mp4', inline: false },
    mov: { mime: 'video/quicktime', inline: false },
    zip: { mime: 'application/zip', inline: false }
  };

  var LIMITS = {
    maxFileBytes: 25 * 1024 * 1024,
    maxFilesPerDraft: 500,
    text: 200,
    comment: 5000,
    finalComment: 20000,
    url: 2000,
    linksPerList: 50
  };

  function extOf(name) {
    var m = /\.([a-z0-9]+)$/i.exec(String(name || ''));
    return m ? m[1].toLowerCase() : '';
  }

  function str(v, max) {
    if (v === null || v === undefined) return '';
    return String(v).replace(/\u0000/g, '').trim().slice(0, max);
  }

  // "12,99" / "1.234,50" / "12.99" -> 12.99 ; ungueltig -> NaN
  function parsePrice(v) {
    var s = String(v === undefined || v === null ? '' : v).replace(/\s|€/g, '');
    if (!s) return NaN;
    if (s.indexOf(',') !== -1) s = s.replace(/\./g, '').replace(',', '.');
    if (!/^\d+(\.\d{1,2})?$/.test(s)) return NaN;
    return Number(s);
  }

  function formatPrice(n) {
    if (typeof n !== 'number' || isNaN(n)) return '';
    return n.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';
  }

  // Ergaenzt fehlendes Schema, liefert '' fuer leere und null fuer ungueltige Links
  function normalizeUrl(v) {
    var s = str(v, LIMITS.url);
    if (!s) return '';
    if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(s)) s = 'https://' + s;
    try {
      var u = new URL(s);
      if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
      if (!u.hostname || u.hostname.indexOf('.') === -1 && u.hostname !== 'localhost') return null;
      return u.href;
    } catch (e) {
      return null;
    }
  }

  function yesNo(v) {
    return v === 'ja' || v === 'nein' ? v : '';
  }

  function linkList(list) {
    if (!Array.isArray(list)) return [];
    return list.slice(0, LIMITS.linksPerList).map(function (l) { return str(l, LIMITS.url); });
  }

  function emptyAnswers() {
    return {
      vat: { value: '', comment: '' },
      delivery: { value: '', days: '', comment: '' },
      category: { value: '', comment: '' },
      supplier: { name: '', links: [''], access: 'none', shareNote: '', comment: '' },
      price: { value: '', comment: '' },
      media: { comment: '', links: [''], files: [] }
    };
  }

  /*
   * Bereinigt Formulardaten auf die bekannten Felder. Unbekannte Felder
   * (z. B. versehentlich mitgeschickte Zugangsdaten) werden verworfen.
   */
  function normalize(input) {
    input = input || {};
    var products = Array.isArray(input.products) ? input.products : [];
    var seen = {};
    var out = { products: [], answers: {}, finalComment: str(input.finalComment, LIMITS.finalComment) };
    products.forEach(function (p) {
      var id = str(p && p.id, 40);
      if (!/^[a-zA-Z0-9_-]{1,40}$/.test(id) || seen[id]) return;
      seen[id] = true;
      out.products.push({ id: id, name: str(p.name, LIMITS.text), articleNumber: str(p.articleNumber, LIMITS.text) });
      var a = (input.answers && input.answers[id]) || {};
      var e = emptyAnswers();
      var vat = a.vat || {}, del = a.delivery || {}, cat = a.category || {}, sup = a.supplier || {}, pr = a.price || {}, med = a.media || {};
      out.answers[id] = {
        vat: { value: yesNo(vat.value), comment: str(vat.comment, LIMITS.comment) },
        delivery: {
          value: yesNo(del.value),
          days: yesNo(del.value) === 'ja' ? str(del.days, 10) : '',
          comment: str(del.comment, LIMITS.comment)
        },
        category: { value: str(cat.value, LIMITS.text), comment: str(cat.comment, LIMITS.comment) },
        supplier: {
          name: str(sup.name, LIMITS.text),
          links: linkList(sup.links),
          access: ACCESS_MODES[sup.access] ? sup.access : e.supplier.access,
          shareNote: sup.access === 'share' ? str(sup.shareNote, LIMITS.comment) : '',
          comment: str(sup.comment, LIMITS.comment)
        },
        price: { value: str(pr.value, 30), comment: str(pr.comment, LIMITS.comment) },
        media: {
          comment: str(med.comment, LIMITS.comment),
          links: linkList(med.links),
          files: Array.isArray(med.files)
            ? med.files.map(function (f) { return str(f && typeof f === 'object' ? f.id : f, 64); })
                .filter(function (id) { return /^[a-f0-9]{32}$/.test(id); })
            : []
        }
      };
    });
    return out;
  }

  function err(list, pid, section, field, message) {
    list.push({ productId: pid, section: section, field: field, message: message });
  }

  /*
   * Prueft Pflichtangaben. credentials: { [productId]: {username, password} }
   * (nur im Browser bzw. beim Absenden vorhanden).
   */
  function validate(data, credentials) {
    var errors = [];
    if (!data.products.length) {
      errors.push({ productId: null, section: null, field: 'products', message: 'Bitte fügen Sie mindestens ein Produkt hinzu.' });
      return errors;
    }
    data.products.forEach(function (p) {
      var a = data.answers[p.id];
      if (!a.vat.value) err(errors, p.id, 'vat', 'value', 'Bitte wählen Sie „Ja“ oder „Nein“.');
      if (!a.delivery.value) err(errors, p.id, 'delivery', 'value', 'Bitte wählen Sie „Ja“ oder „Nein“.');
      if (a.delivery.value === 'ja') {
        var d = String(a.delivery.days).trim();
        if (!d) err(errors, p.id, 'delivery', 'days', 'Bitte geben Sie die Anzahl der Tage an.');
        else if (!/^\d+$/.test(d) || +d < 1 || +d > 3650) err(errors, p.id, 'delivery', 'days', 'Bitte eine ganze Zahl zwischen 1 und 3650 eingeben.');
      }
      if (!a.category.value) err(errors, p.id, 'category', 'value', 'Bitte geben Sie eine Kategorie an.');
      if (!a.supplier.name) err(errors, p.id, 'supplier', 'name', 'Bitte geben Sie den Namen des Lieferanten an.');
      a.supplier.links.forEach(function (l, i) {
        if (l && normalizeUrl(l) === null) err(errors, p.id, 'supplier', 'links.' + i, 'Bitte einen gültigen Link eingeben (z. B. https://lieferant.de).');
      });
      if (a.supplier.access === 'store') {
        var c = (credentials && credentials[p.id]) || {};
        if (!str(c.username, 500) && !str(c.password, 500)) {
          err(errors, p.id, 'supplier', 'credentials', 'Bitte Zugangsdaten eintragen oder eine andere Zugangsart wählen.');
        }
      }
      if (!a.price.value) err(errors, p.id, 'price', 'value', 'Bitte geben Sie einen Verkaufspreis an.');
      else {
        var n = parsePrice(a.price.value);
        if (isNaN(n) || n > 10000000) err(errors, p.id, 'price', 'value', 'Bitte einen gültigen Betrag eingeben (z. B. 19,99).');
      }
      a.media.links.forEach(function (l, i) {
        if (l && normalizeUrl(l) === null) err(errors, p.id, 'media', 'links.' + i, 'Bitte einen gültigen Link eingeben (z. B. https://…).');
      });
    });
    return errors;
  }

  // Status eines Abschnitts fuer ein Produkt: 'complete' | 'partial' | 'empty'
  function sectionStatus(key, a) {
    switch (key) {
      case 'vat':
        return a.vat.value ? 'complete' : (a.vat.comment ? 'partial' : 'empty');
      case 'delivery':
        if (a.delivery.value === 'nein') return 'complete';
        if (a.delivery.value === 'ja') return /^\d+$/.test(String(a.delivery.days).trim()) && +a.delivery.days >= 1 ? 'complete' : 'partial';
        return a.delivery.comment ? 'partial' : 'empty';
      case 'category':
        return a.category.value ? 'complete' : (a.category.comment ? 'partial' : 'empty');
      case 'supplier':
        return a.supplier.name ? 'complete' : (a.supplier.comment || a.supplier.links.some(Boolean) ? 'partial' : 'empty');
      case 'price':
        return a.price.value && !isNaN(parsePrice(a.price.value)) ? 'complete' : (a.price.value || a.price.comment ? 'partial' : 'empty');
      case 'media':
        return a.media.comment || a.media.links.some(Boolean) || a.media.files.length ? 'complete' : 'empty';
    }
    return 'empty';
  }

  function formatBytes(n) {
    if (n < 1024) return n + ' B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(0) + ' KB';
    return (n / 1024 / 1024).toFixed(1).replace('.', ',') + ' MB';
  }

  var api = {
    SECTIONS: SECTIONS,
    ACCESS_MODES: ACCESS_MODES,
    FILE_TYPES: FILE_TYPES,
    LIMITS: LIMITS,
    extOf: extOf,
    parsePrice: parsePrice,
    formatPrice: formatPrice,
    normalizeUrl: normalizeUrl,
    emptyAnswers: emptyAnswers,
    normalize: normalize,
    validate: validate,
    sectionStatus: sectionStatus,
    formatBytes: formatBytes
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.FormSchema = api;
})(this);
