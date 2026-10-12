'use strict';
/*
 * Minimaler PDF-Generator ohne externe Abhaengigkeiten.
 * Nutzt die PDF-Standardschriften Helvetica / Helvetica-Bold mit
 * WinAnsiEncoding (deckt deutsche Umlaute, ß und € ab), automatischen
 * Zeilenumbruch, Seitenumbruch und Seitenzahlen.
 */
const zlib = require('node:zlib');

// Zeichenbreiten (1/1000 em) fuer ASCII 32..126
const W_REG = [278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584];
const W_BOLD = [278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584];

// Unicode -> WinAnsi-Byte fuer Zeichen ausserhalb von Latin-1
const WIN_ANSI = {
  0x20ac: 0x80, 0x201a: 0x82, 0x0192: 0x83, 0x201e: 0x84, 0x2026: 0x85, 0x2020: 0x86, 0x2021: 0x87,
  0x02c6: 0x88, 0x2030: 0x89, 0x0160: 0x8a, 0x2039: 0x8b, 0x0152: 0x8c, 0x017d: 0x8e, 0x2018: 0x91,
  0x2019: 0x92, 0x201c: 0x93, 0x201d: 0x94, 0x2022: 0x95, 0x2013: 0x96, 0x2014: 0x97, 0x02dc: 0x98,
  0x2122: 0x99, 0x0161: 0x9a, 0x203a: 0x9b, 0x0153: 0x9c, 0x017e: 0x9e, 0x0178: 0x9f
};
const SPECIAL_WIDTH = { 0x80: 556, 0x84: 333, 0x85: 1000, 0x91: 222, 0x92: 222, 0x93: 333, 0x94: 333, 0x95: 350, 0x96: 556, 0x97: 1000, 0xdf: 611, 0xd7: 584 };

function toWinAnsi(text) {
  const bytes = [];
  for (const ch of String(text)) {
    const cp = ch.codePointAt(0);
    if (cp === 9) bytes.push(32, 32, 32, 32);
    else if (cp >= 32 && cp <= 126) bytes.push(cp);
    else if (cp >= 160 && cp <= 255) bytes.push(cp);
    else if (WIN_ANSI[cp]) bytes.push(WIN_ANSI[cp]);
    else if (cp >= 32) bytes.push(63); // '?'
  }
  return bytes;
}

function charWidth(b, bold) {
  const table = bold ? W_BOLD : W_REG;
  if (b >= 32 && b <= 126) return table[b - 32];
  if (SPECIAL_WIDTH[b]) return SPECIAL_WIDTH[b];
  if (b >= 0xc0 && b <= 0xdd) return bold ? 722 : 667; // Grossbuchstaben mit Akzent
  if (b >= 0xe0) return 556; // Kleinbuchstaben mit Akzent
  return 556;
}

function textWidth(bytes, size, bold) {
  let w = 0;
  for (const b of bytes) w += charWidth(b, bold);
  return (w * size) / 1000;
}

function escapeBytes(bytes) {
  let s = '';
  for (const b of bytes) {
    if (b === 0x28 || b === 0x29 || b === 0x5c) s += '\\' + String.fromCharCode(b);
    else if (b < 32 || b > 126) s += '\\' + b.toString(8).padStart(3, '0');
    else s += String.fromCharCode(b);
  }
  return s;
}

function hexToRgb(hex) {
  const n = parseInt(hex.replace('#', ''), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255].map((v) => v.toFixed(3)).join(' ');
}

class PdfDocument {
  constructor(opts = {}) {
    this.width = 595.28; // A4
    this.height = 841.89;
    this.margin = 56;
    this.footerText = opts.footer || '';
    this.title = opts.title || 'Dokument';
    this.pages = [];
    this.newPage();
  }

  get contentWidth() {
    return this.width - this.margin * 2;
  }

  newPage() {
    this.page = [];
    this.pages.push(this.page);
    this.y = this.height - this.margin;
  }

  ensure(h) {
    if (this.y - h < this.margin + 24) this.newPage();
  }

  _text(x, y, bytes, size, bold, color) {
    this.page.push(`BT /${bold ? 'F2' : 'F1'} ${size} Tf ${hexToRgb(color)} rg ${x.toFixed(2)} ${y.toFixed(2)} Td (${escapeBytes(bytes)}) Tj ET`);
  }

  // Bricht Text auf Breite um; harte Zeilenumbrueche bleiben erhalten
  wrap(text, size, bold, maxWidth) {
    const lines = [];
    for (const para of String(text).split(/\r?\n/)) {
      const words = para.split(/ +/);
      let line = [];
      for (const word of words) {
        let wb = toWinAnsi(word);
        const candidate = line.length ? line.concat([32], wb) : wb;
        if (textWidth(candidate, size, bold) <= maxWidth) {
          line = candidate;
          continue;
        }
        if (line.length) lines.push(line);
        // sehr lange Woerter (z. B. URLs) hart umbrechen
        while (textWidth(wb, size, bold) > maxWidth) {
          let cut = wb.length;
          while (cut > 1 && textWidth(wb.slice(0, cut), size, bold) > maxWidth) cut--;
          lines.push(wb.slice(0, cut));
          wb = wb.slice(cut);
        }
        line = wb;
      }
      lines.push(line);
    }
    return lines;
  }

  text(str, o = {}) {
    const size = o.size || 10;
    const bold = !!o.bold;
    const color = o.color || '#111827';
    const indent = o.indent || 0;
    const lh = o.lineHeight || size * 1.4;
    const lines = this.wrap(str, size, bold, this.contentWidth - indent);
    for (const line of lines) {
      this.ensure(lh);
      this.y -= lh;
      this._text(this.margin + indent, this.y + (lh - size) / 2, line, size, bold, color);
    }
    if (o.after) this.y -= o.after;
  }

  // Beschriftung links, Wert rechts daneben (umbrechend)
  field(label, value, o = {}) {
    const size = o.size || 10;
    const indent = o.indent || 0;
    const labelW = o.labelWidth || 150;
    const lh = size * 1.45;
    const valueLines = this.wrap(value === '' || value === null || value === undefined ? '–' : value, size, !!o.boldValue, this.contentWidth - indent - labelW);
    const labelLines = this.wrap(label, size, false, labelW - 8);
    const n = Math.max(valueLines.length, labelLines.length);
    for (let i = 0; i < n; i++) {
      this.ensure(lh);
      this.y -= lh;
      const by = this.y + (lh - size) / 2;
      if (labelLines[i]) this._text(this.margin + indent, by, labelLines[i], size, false, '#6b7280');
      if (valueLines[i]) this._text(this.margin + indent + labelW, by, valueLines[i], size, !!o.boldValue, o.color || '#111827');
    }
    this.y -= 2;
  }

  rule(o = {}) {
    const gap = o.gap === undefined ? 8 : o.gap;
    this.ensure(gap * 2);
    this.y -= gap;
    this.page.push(`${hexToRgb(o.color || '#e5e7eb')} RG ${o.width || 0.8} w ${this.margin} ${this.y.toFixed(2)} m ${(this.width - this.margin).toFixed(2)} ${this.y.toFixed(2)} l S`);
    this.y -= gap;
  }

  // Farbiger Balken mit Text (fuer Produkt-Ueberschriften)
  band(str, o = {}) {
    const size = o.size || 13;
    const h = size + 14;
    this.ensure(h + 30);
    this.y -= h;
    this.page.push(`${hexToRgb(o.bg || '#f3f4f6')} rg ${this.margin} ${this.y.toFixed(2)} ${this.contentWidth.toFixed(2)} ${h} re f`);
    const lines = this.wrap(str, size, true, this.contentWidth - 20);
    this._text(this.margin + 10, this.y + (h - size) / 2 + 2, lines[0] || [], size, true, o.color || '#111827');
    this.y -= o.after === undefined ? 8 : o.after;
  }

  space(n) {
    this.y -= n;
  }

  toBuffer() {
    const objects = [];
    const add = (body) => {
      objects.push(body);
      return objects.length;
    };
    const catalogId = add(null);
    const pagesId = add(null);
    const f1 = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>');
    const f2 = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>');
    const pageIds = [];
    const total = this.pages.length;
    this.pages.forEach((ops, i) => {
      const footer = [];
      const fs = 8;
      const left = toWinAnsi(this.footerText);
      const right = toWinAnsi(`Seite ${i + 1} von ${total}`);
      footer.push(`${hexToRgb('#e5e7eb')} RG 0.6 w ${this.margin} ${this.margin} m ${(this.width - this.margin).toFixed(2)} ${this.margin} l S`);
      footer.push(`BT /F1 ${fs} Tf ${hexToRgb('#6b7280')} rg ${this.margin} ${this.margin - 14} Td (${escapeBytes(left)}) Tj ET`);
      footer.push(`BT /F1 ${fs} Tf ${hexToRgb('#6b7280')} rg ${(this.width - this.margin - textWidth(right, fs, false)).toFixed(2)} ${this.margin - 14} Td (${escapeBytes(right)}) Tj ET`);
      const stream = zlib.deflateSync(Buffer.from(ops.concat(footer).join('\n'), 'latin1'));
      const contentId = add({ dict: `<< /Length ${stream.length} /Filter /FlateDecode >>`, stream });
      pageIds.push(add(`<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 ${this.width} ${this.height}] /Resources << /Font << /F1 ${f1} 0 R /F2 ${f2} 0 R >> >> /Contents ${contentId} 0 R >>`));
    });
    objects[catalogId - 1] = `<< /Type /Catalog /Pages ${pagesId} 0 R >>`;
    objects[pagesId - 1] = `<< /Type /Pages /Kids [${pageIds.map((id) => id + ' 0 R').join(' ')}] /Count ${pageIds.length} >>`;
    const infoId = add(`<< /Title (${escapeBytes(toWinAnsi(this.title))}) /Producer (Produktformular) /CreationDate (D:${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)}Z) >>`);

    const chunks = [Buffer.from('%PDF-1.4\n%\xe2\xe3\xcf\xd3\n', 'latin1')];
    let offset = chunks[0].length;
    const offsets = [];
    objects.forEach((obj, i) => {
      offsets.push(offset);
      let buf;
      if (obj && typeof obj === 'object') {
        buf = Buffer.concat([Buffer.from(`${i + 1} 0 obj\n${obj.dict}\nstream\n`, 'latin1'), obj.stream, Buffer.from('\nendstream\nendobj\n', 'latin1')]);
      } else {
        buf = Buffer.from(`${i + 1} 0 obj\n${obj}\nendobj\n`, 'latin1');
      }
      chunks.push(buf);
      offset += buf.length;
    });
    let xref = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    for (const o of offsets) xref += String(o).padStart(10, '0') + ' 00000 n \n';
    xref += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogId} 0 R /Info ${infoId} 0 R >>\nstartxref\n${offset}\n%%EOF\n`;
    chunks.push(Buffer.from(xref, 'latin1'));
    return Buffer.concat(chunks);
  }
}

module.exports = { PdfDocument };
