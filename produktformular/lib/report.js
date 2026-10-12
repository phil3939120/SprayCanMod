'use strict';
/*
 * Erstellt den PDF-Export einer Einreichung. Zugangsdaten werden hier
 * grundsaetzlich nicht ausgegeben – nur der Hinweis, dass sie
 * verschluesselt hinterlegt sind.
 */
const { PdfDocument } = require('./pdf');
const Schema = require('../public/js/schema');

function yn(v) {
  return v === 'ja' ? 'Ja' : v === 'nein' ? 'Nein' : '–';
}

function productLabel(p, i) {
  return `Produkt ${i + 1}` + (p.name ? ` – ${p.name}` : '');
}

function fmtDate(iso) {
  return new Date(iso).toLocaleString('de-DE', { timeZone: 'Europe/Berlin', dateStyle: 'medium', timeStyle: 'short' }) + ' Uhr';
}

/*
 * submission: { reference, submittedAt, data, files: [{id, productId, originalName, size, mime}],
 *               credentialProducts: Set<productId> }
 */
function buildSubmissionPdf(submission) {
  const { data } = submission;
  const doc = new PdfDocument({
    title: `Produkterfassung ${submission.reference}`,
    footer: `Produkterfassung · Referenz ${submission.reference} · Vertraulich`
  });
  const filesById = new Map(submission.files.map((f) => [f.id, f]));

  doc.text('Produkterfassung', { size: 22, bold: true, lineHeight: 28 });
  doc.text(`Referenz ${submission.reference} · eingereicht am ${fmtDate(submission.submittedAt)}`, { size: 10, color: '#6b7280', after: 6 });
  doc.rule({ gap: 6 });

  doc.text('Produktübersicht', { size: 13, bold: true, lineHeight: 20 });
  data.products.forEach((p, i) => {
    doc.field(`Produkt ${i + 1}`, [p.name || 'Ohne Namen', p.articleNumber ? `Art.-Nr. ${p.articleNumber}` : ''].filter(Boolean).join(' · '));
  });
  doc.space(10);

  data.products.forEach((p, i) => {
    const a = data.answers[p.id];
    doc.band(productLabel(p, i), { after: 4 });
    doc.field('Produktname', p.name);
    doc.field('Artikelnummer', p.articleNumber);

    const section = (nr) => {
      const s = Schema.SECTIONS[nr - 1];
      doc.space(6);
      doc.ensure(60); // Ueberschrift nicht allein am Seitenende
      doc.text(`${s.nr}. ${s.title}`, { size: 10.5, bold: true, lineHeight: 16, color: '#1d4ed8' });
    };
    const comment = (c) => doc.field('Kommentar', c);

    section(1);
    doc.field('Mehrwertsteuerfrei', yn(a.vat.value), { boldValue: true });
    comment(a.vat.comment);

    section(2);
    doc.field('Lieferzeit verlängern', yn(a.delivery.value), { boldValue: true });
    if (a.delivery.value === 'ja') doc.field('Verlängerung um', `${a.delivery.days} Tage`, { boldValue: true });
    comment(a.delivery.comment);

    section(3);
    doc.field('Kategorie', a.category.value, { boldValue: true });
    comment(a.category.comment);

    section(4);
    doc.field('Lieferant', a.supplier.name, { boldValue: true });
    const sl = a.supplier.links.filter(Boolean);
    doc.field(sl.length > 1 ? 'Lieferanten-Links' : 'Lieferanten-Link', sl.length ? sl.join('\n') : '–');
    let access = Schema.ACCESS_MODES[a.supplier.access];
    if (a.supplier.access === 'store') {
      access = submission.credentialProducts.has(p.id)
        ? 'Verschlüsselt hinterlegt – nur für berechtigte Personen im geschützten Admin-Bereich einsehbar. Nicht Bestandteil dieses Dokuments.'
        : 'Keine Zugangsdaten hinterlegt';
    }
    doc.field('Zugang', access);
    if (a.supplier.access === 'share') doc.field('Hinweis zur Freigabe', a.supplier.shareNote);
    comment(a.supplier.comment);

    section(5);
    const price = Schema.parsePrice(a.price.value);
    doc.field('Verkaufspreis', isNaN(price) ? a.price.value : Schema.formatPrice(price), { boldValue: true });
    comment(a.price.comment);

    section(6);
    comment(a.media.comment);
    const ml = a.media.links.filter(Boolean);
    doc.field(ml.length > 1 ? 'Medien-Links' : 'Medien-Link', ml.length ? ml.join('\n') : '–');
    const files = a.media.files.map((id) => filesById.get(id)).filter(Boolean);
    doc.field('Dateien', files.length ? files.map((f, k) => `${k + 1}. ${f.originalName} (${Schema.formatBytes(f.size)}) · Datei-ID ${f.id.slice(0, 8)}`).join('\n') : '–');
    doc.space(14);
  });

  doc.band('Abschließende Bemerkungen / Schlusswort', { after: 6 });
  doc.text(data.finalComment || '–', { size: 10 });
  doc.space(12);
  doc.text('Hochgeladene Dateien sind ausschließlich für berechtigte Personen über den geschützten Admin-Bereich abrufbar.', { size: 8.5, color: '#6b7280' });

  return doc.toBuffer();
}

module.exports = { buildSubmissionPdf };
