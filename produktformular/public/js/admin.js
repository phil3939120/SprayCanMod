/* Admin-Bereich: Einreichungen, Dateien, PDF und Zugangsdaten (nur nach Anmeldung) */
(function () {
  'use strict';
  var S = window.FormSchema;
  var esc = window.Summary.esc;
  var root = document.getElementById('admin');
  var logoutBtn = document.getElementById('logout');
  var current = null;

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
          throw e;
        }
        return data;
      });
    });
  }

  function fmt(iso) {
    return new Date(iso).toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
  }

  function handle(e) {
    if (e.status === 401) return showLogin();
    alert(e.message);
  }

  function showLogin(msg) {
    logoutBtn.hidden = true;
    root.innerHTML = '<form class="login" id="login-form"><h1>Anmelden</h1><p>Zugriff nur für berechtigte Personen.</p>' +
      (msg ? '<div class="notice notice-error">' + esc(msg) + '</div>' : '') +
      '<div class="field"><label for="pw">Passwort</label><input class="input" type="password" id="pw" autocomplete="current-password" required></div>' +
      '<button class="btn btn-primary" type="submit">Anmelden</button></form>';
    document.getElementById('pw').focus();
    document.getElementById('login-form').addEventListener('submit', function (e) {
      e.preventDefault();
      api('POST', '/api/admin/login', { password: document.getElementById('pw').value })
        .then(showList)
        .catch(function (err) { showLogin(err.message); });
    });
  }

  function showList() {
    logoutBtn.hidden = false;
    history.replaceState(null, '', '/admin');
    api('GET', '/api/admin/submissions').then(function (r) {
      var rows = r.submissions.map(function (s) {
        return '<tr data-id="' + esc(s.id) + '" tabindex="0"><td class="mono">' + esc(s.reference) + '</td><td>' + esc(fmt(s.submittedAt)) + '</td>' +
          '<td>' + s.productCount + (s.productNames.length ? ' <span class="field-help">(' + esc(s.productNames.join(', ')) + ')</span>' : '') + '</td><td>' + s.fileCount + '</td></tr>';
      }).join('');
      root.innerHTML = '<div class="admin-head"><h1>Einreichungen</h1><span class="field-help">' + r.submissions.length + ' insgesamt</span></div>' +
        (r.submissions.length
          ? '<div class="table-wrap"><table><thead><tr><th>Referenz</th><th>Eingegangen</th><th>Produkte</th><th>Dateien</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
          : '<div class="table-wrap"><div class="empty">Noch keine Einreichungen vorhanden.</div></div>');
      Array.prototype.forEach.call(root.querySelectorAll('tbody tr'), function (tr) {
        var open = function () { showDetail(tr.getAttribute('data-id')); };
        tr.addEventListener('click', open);
        tr.addEventListener('keydown', function (e) { if (e.key === 'Enter') open(); });
      });
    }).catch(handle);
  }

  function showDetail(id) {
    api('GET', '/api/admin/submissions/' + encodeURIComponent(id)).then(function (r) {
      var s = r.submission;
      current = s;
      var files = {};
      s.files.forEach(function (f) {
        files[f.id] = { id: f.id, name: f.originalName, size: f.size, image: !!(S.FILE_TYPES[f.ext] || {}).image };
      });
      var withCreds = {};
      s.credentialProducts.forEach(function (pid) { withCreds[pid] = true; });
      root.innerHTML = '<div class="admin-head"><div><button type="button" class="btn-link" id="back">← Zur Übersicht</button>' +
        '<h1>Referenz <span class="mono">' + esc(s.reference) + '</span></h1><span class="field-help">Eingegangen am ' + esc(fmt(s.submittedAt)) + '</span></div>' +
        '<div class="detail-actions"><a class="btn btn-primary" href="/api/admin/submissions/' + esc(s.id) + '/pdf">PDF herunterladen</a>' +
        '<button type="button" class="btn btn-secondary" id="delete">Löschen</button></div></div>' +
        window.Summary.render(s.data, {
          files: files,
          linkFiles: true,
          fileUrl: function (fid, download) { return '/api/files/' + encodeURIComponent(fid) + (download ? '?download=1' : ''); },
          accessHtml: function (p, a) {
            if (a.supplier.access !== 'store') return esc(S.ACCESS_MODES[a.supplier.access]);
            if (!withCreds[p.id]) return 'Keine Zugangsdaten hinterlegt';
            return '<div class="cred"><span class="tag tag-lock">Verschlüsselt hinterlegt</span>' +
              '<button type="button" class="btn-link" data-reveal="' + esc(p.id) + '">Zugangsdaten anzeigen (wird protokolliert)</button>' +
              '<div class="cred-values" data-cred="' + esc(p.id) + '" hidden></div></div>';
          }
        });
      document.getElementById('back').addEventListener('click', showList);
      document.getElementById('delete').addEventListener('click', function () {
        if (!confirm('Einreichung ' + s.reference + ' mit allen Dateien und Zugangsdaten endgültig löschen?')) return;
        api('DELETE', '/api/admin/submissions/' + encodeURIComponent(s.id)).then(showList).catch(handle);
      });
      Array.prototype.forEach.call(root.querySelectorAll('[data-reveal]'), function (btn) {
        btn.addEventListener('click', function () {
          var pid = btn.getAttribute('data-reveal');
          var box = root.querySelector('[data-cred="' + pid + '"]');
          if (!box.hidden) {
            box.hidden = true;
            box.textContent = '';
            btn.textContent = 'Zugangsdaten anzeigen (wird protokolliert)';
            return;
          }
          api('POST', '/api/admin/submissions/' + encodeURIComponent(current.id) + '/credentials/' + encodeURIComponent(pid)).then(function (res) {
            var c = res.credentials;
            box.textContent = 'Benutzer: ' + (c.username || '–') + '\nPasswort: ' + (c.password || '–') + (c.note ? '\nHinweis:  ' + c.note : '');
            box.hidden = false;
            btn.textContent = 'Zugangsdaten verbergen';
          }).catch(handle);
        });
      });
      window.scrollTo(0, 0);
    }).catch(handle);
  }

  logoutBtn.addEventListener('click', function () {
    api('POST', '/api/admin/logout').then(function () { showLogin(); });
  });

  api('GET', '/api/admin/me').then(function (r) {
    if (r.admin) showList();
    else showLogin();
  }).catch(function () { showLogin('Server nicht erreichbar.'); });
})();
