/* Zusammenfassung einer Einreichung als HTML (Pruefansicht und Admin-Bereich). */
(function (root) {
  'use strict';
  var S = root.FormSchema;

  function esc(v) {
    return String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function row(label, value, strong) {
    var empty = value === '' || value === null || value === undefined;
    return '<div class="summary-row"><dt>' + esc(label) + '</dt><dd class="' + (strong ? 'strong' : '') + '">' +
      (empty ? '–' : value) + '</dd></div>';
  }

  function yn(v) {
    return v === 'ja' ? 'Ja' : v === 'nein' ? 'Nein' : '';
  }

  function links(list) {
    var l = list.filter(Boolean);
    if (!l.length) return '';
    return l.map(function (u) {
      var href = S.normalizeUrl(u);
      return href ? '<a href="' + esc(href) + '" target="_blank" rel="noopener noreferrer">' + esc(u) + '</a>' : esc(u);
    }).join('<br>');
  }

  function fileChips(ids, opts) {
    var items = ids.map(function (id) { return opts.files[id]; }).filter(Boolean);
    if (!items.length) return '';
    return '<div class="summary-files">' + items.map(function (f) {
      var thumb = f.image
        ? '<span class="file-thumb"><img src="' + esc(opts.fileUrl(f.id)) + '" alt="" loading="lazy"></span>'
        : '<span class="file-thumb">' + esc(S.extOf(f.name)) + '</span>';
      var inner = thumb + '<span>' + esc(f.name) + ' · ' + esc(S.formatBytes(f.size)) + '</span>';
      return opts.linkFiles
        ? '<a class="summary-file" href="' + esc(opts.fileUrl(f.id, true)) + '">' + inner + '</a>'
        : '<span class="summary-file">' + inner + '</span>';
    }).join('') + '</div>';
  }

  /*
   * data: normalisierte Formulardaten
   * opts: { files: {id: meta}, fileUrl(id, download), linkFiles, accessHtml(product, answers) }
   */
  function render(data, opts) {
    var html = '';
    data.products.forEach(function (p, i) {
      var a = data.answers[p.id];
      var price = S.parsePrice(a.price.value);
      html += '<article class="summary-product"><h3><span class="product-badge">Produkt ' + (i + 1) + '</span>' +
        esc(p.name || 'Ohne Namen') + (p.articleNumber ? ' <small>Art.-Nr. ' + esc(p.articleNumber) + '</small>' : '') + '</h3>';

      html += '<div class="summary-section"><h4>1. Mehrwertsteuer</h4><dl>' +
        row('Mehrwertsteuerfrei', esc(yn(a.vat.value)), true) + row('Kommentar', esc(a.vat.comment)) + '</dl></div>';

      html += '<div class="summary-section"><h4>2. Lieferwartezeit</h4><dl>' +
        row('Verlängern', esc(yn(a.delivery.value)), true) +
        (a.delivery.value === 'ja' ? row('Verlängerung um', esc(a.delivery.days ? a.delivery.days + ' Tage' : ''), true) : '') +
        row('Kommentar', esc(a.delivery.comment)) + '</dl></div>';

      html += '<div class="summary-section"><h4>3. Kategorie</h4><dl>' +
        row('Kategorie', esc(a.category.value), true) + row('Kommentar', esc(a.category.comment)) + '</dl></div>';

      html += '<div class="summary-section"><h4>4. Lieferant</h4><dl>' +
        row('Lieferant', esc(a.supplier.name), true) +
        row('Links', links(a.supplier.links)) +
        row('Zugang', opts.accessHtml ? opts.accessHtml(p, a) : esc(S.ACCESS_MODES[a.supplier.access])) +
        (a.supplier.access === 'share' ? row('Hinweis zur Freigabe', esc(a.supplier.shareNote)) : '') +
        row('Kommentar', esc(a.supplier.comment)) + '</dl></div>';

      html += '<div class="summary-section"><h4>5. Preis</h4><dl>' +
        row('Verkaufspreis', esc(isNaN(price) ? a.price.value : S.formatPrice(price)), true) +
        row('Kommentar', esc(a.price.comment)) + '</dl></div>';

      html += '<div class="summary-section"><h4>6. Medien</h4><dl>' +
        row('Kommentar', esc(a.media.comment)) +
        row('Links', links(a.media.links)) +
        row('Dateien', fileChips(a.media.files, opts)) + '</dl></div>';

      html += '</article>';
    });
    html += '<div class="summary-final"><h3>Abschließende Bemerkungen / Schlusswort</h3><p>' + esc(data.finalComment || '–') + '</p></div>';
    return html;
  }

  root.Summary = { render: render, esc: esc };
})(this);
