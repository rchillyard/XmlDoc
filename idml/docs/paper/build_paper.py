"""Build the manuscript and its figures from Markdown and archived measurements.

Run with the Codex bundled Python runtime (python-docx, reportlab, Pillow).
The bundled Node/sharp runtime rasterizes vector figures for Word.
No benchmark is executed and no source evidence is modified.
"""

from __future__ import annotations

import collections
import hashlib
import json
import math
import os
import re
import statistics
import subprocess
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor
from reportlab.graphics import renderSVG
from reportlab.graphics.charts.barcharts import VerticalBarChart
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon
from reportlab.lib import colors

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
EVIDENCE = REPO / "idml/docs/evidence"
FIGURES = HERE / "figures"
FIGURES.mkdir(exist_ok=True)
RUNTIME = Path(os.environ.get("CODEX_DOCUMENT_RUNTIME", str(Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies")))
OUTPUT = HERE / "three-way-merge-paper-en.docx"


def svg_png(drawing: Drawing, name: str) -> None:
    svg = FIGURES / f"{name}.svg"
    png = FIGURES / f"{name}.png"
    svg.write_text(renderSVG.drawToString(drawing), encoding="utf-8")
    js = "const sharp=require(process.argv[1]);sharp(process.argv[2],{density:220}).png().toFile(process.argv[3]).catch(e=>{console.error(e);process.exit(1)});"
    subprocess.run([str(RUNTIME / "node/bin/node"), "-e", js,
                    str(RUNTIME / "node/node_modules/sharp"), str(svg), str(png)], check=True)


def figures_and_statistics() -> list[dict]:
    formal_path = EVIDENCE / "mr13-formal.json"
    raw = json.loads(formal_path.read_text())
    groups = collections.defaultdict(list)
    for run in raw["runs"]:
        groups[(run["nodes"], run["budget"], run["textBytesPerLeaf"])].extend(run["measurements"])
    rows = []
    for (nodes, budget, text_bytes), observations in groups.items():
        item = dict(nodes=nodes, operationBudget=budget, textBytesPerLeaf=text_bytes, observations=len(observations))
        for metric in ("mergeMs", "totalMs"):
            values = sorted(m[metric] for m in observations)
            item[metric] = dict(median=statistics.median(values), p95=values[math.ceil(.95 * len(values)) - 1])
        rows.append(item)
    stats = dict(source="../evidence/mr13-formal.json", sourceSha256=hashlib.sha256(formal_path.read_bytes()).hexdigest(),
                 sourceCommit=raw["sourceCommit"], aggregation="Pool seeds within size/budget/text group; nearest-rank p95", groups=rows)
    (HERE / "paper-statistics.json").write_text(json.dumps(stats, indent=2) + "\n")

    d = Drawing(720, 205)
    labels = [
        ("Correspondence", ["Self and anonymous nodes", "Identity and ambiguity"]),
        ("Tree reconciliation", ["PCS and attributes", "Reachable edits and rebuild"]),
        ("Package validation", ["Frame / story references", "Concurrent dependencies"]),
        ("Output transaction", ["Stage and re-open", "Atomic replacement"]),
    ]
    for i, (label, lines) in enumerate(labels):
        x = 5 + i * 180
        d.add(Rect(x, 94, 165, 91, fillColor=colors.HexColor("#F1F5F8"), strokeColor=colors.HexColor("#8C9DA9"), strokeWidth=.8))
        d.add(String(x + 82.5, 160, label, textAnchor="middle", fontName="Helvetica-Bold", fontSize=13.5))
        for j, line in enumerate(lines):
            d.add(String(x + 82.5, 137 - j * 18, line, textAnchor="middle", fontName="Helvetica", fontSize=12))
        if i < 3:
            d.add(Line(x + 165, 138, x + 177, 138, strokeColor=colors.HexColor("#415362")))
            d.add(Polygon([x + 173, 141, x + 179, 138, x + 173, 135], fillColor=colors.HexColor("#415362"), strokeColor=None))
    d.add(Line(15, 67, 705, 67, strokeColor=colors.HexColor("#9AA7AE"), strokeDashArray=[4, 3]))
    d.add(String(360, 43, "Additional observation boundary  |  InDesign open / save / close / reopen", textAnchor="middle", fontName="Helvetica", fontSize=13))
    d.add(String(360, 22, "Real application evidence remains incomplete", textAnchor="middle", fontName="Helvetica-Oblique", fontSize=12))
    svg_png(d, "architecture")

    d = Drawing(720, 307)
    chart = VerticalBarChart()
    chart.x, chart.y, chart.width, chart.height = 53, 55, 650, 210
    chart.data = [[r[m]["median"] for r in rows] for m in ("mergeMs", "totalMs")]
    chart.valueAxis.valueMin, chart.valueAxis.valueMax, chart.valueAxis.valueStep = 0, 30, 5
    chart.valueAxis.labels.fontName = "Helvetica"
    chart.valueAxis.labels.fontSize = 11
    chart.valueAxis.visibleGrid = True
    chart.valueAxis.gridStrokeColor = colors.HexColor("#DCE2E7")
    chart.valueAxis.gridStrokeWidth = .5
    chart.categoryAxis.categoryNames = [f"{r['nodes']} / {r['operationBudget']}" + (" LT" if r['textBytesPerLeaf'] else "") for r in rows]
    chart.categoryAxis.labels.fontName = "Helvetica"
    chart.categoryAxis.labels.fontSize = 10.5
    chart.categoryAxis.labels.dy = -6
    chart.barWidth, chart.groupSpacing, chart.barSpacing = 10, 10, 2
    chart.bars[0].fillColor = colors.HexColor("#345E7A")
    chart.bars[1].fillColor = colors.HexColor("#A7BDCB")
    chart.bars.strokeColor = None
    d.add(chart)
    d.add(String(13, 277, "ms", fontName="Helvetica", fontSize=12))
    d.add(Rect(120, 284, 12, 10, fillColor=colors.HexColor("#345E7A"), strokeColor=None))
    d.add(String(140, 283, "Prematched merge / rebuild", fontName="Helvetica", fontSize=12))
    d.add(Rect(393, 284, 12, 10, fillColor=colors.HexColor("#A7BDCB"), strokeColor=None))
    d.add(String(413, 283, "Total including validation", fontName="Helvetica", fontSize=12))
    d.add(String(380, 16, "Elements / operation budget", textAnchor="middle", fontName="Helvetica", fontSize=12))
    svg_png(d, "performance")
    return rows


def set_font(style, name="Times New Roman", size=11, bold=False):
    style.font.name = name
    style.font.size = Pt(size)
    style.font.bold = bold
    style.font.color.rgb = RGBColor(0, 0, 0)
    fonts = style.element.get_or_add_rPr().get_or_add_rFonts()
    for attr in ("asciiTheme", "hAnsiTheme", "eastAsiaTheme", "cstheme", "csTheme"):
        fonts.attrib.pop(qn("w:" + attr), None)
    fonts.set(qn("w:ascii"), name)
    fonts.set(qn("w:hAnsi"), name)


def hyperlink(paragraph, label, url):
    h = OxmlElement("w:hyperlink")
    rid = paragraph.part.relate_to(url, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink", is_external=True)
    h.set(qn("r:id"), rid)
    r = OxmlElement("w:r")
    props = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "24485E")
    props.append(color)
    r.append(props)
    t = OxmlElement("w:t")
    t.text = label
    r.append(t)
    h.append(r)
    paragraph._p.append(h)


INLINE = re.compile(r"(\[[^\]]+\]\([^\)]+\)|\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)")


def inline(paragraph, text):
    for part in INLINE.split(text):
        if not part:
            continue
        if part.startswith("[") and "](" in part:
            match = re.fullmatch(r"\[([^\]]+)\]\(([^\)]+)\)", part)
            hyperlink(paragraph, match[1], match[2])
        elif part.startswith("**"):
            paragraph.add_run(part[2:-2]).bold = True
        elif part.startswith("`"):
            r = paragraph.add_run(part[1:-1])
            r.font.name = "Courier New"
            r.font.size = Pt(9)
        elif part.startswith("*"):
            paragraph.add_run(part[1:-1]).italic = True
        else:
            paragraph.add_run(part)


def math_run(text):
    r = OxmlElement("m:r")
    rp = OxmlElement("w:rPr")
    font = OxmlElement("w:rFonts")
    font.set(qn("w:ascii"), "Cambria Math")
    font.set(qn("w:hAnsi"), "Cambria Math")
    rp.append(font)
    r.append(rp)
    t = OxmlElement("m:t")
    t.set(qn("xml:space"), "preserve")
    t.text = text
    r.append(t)
    return r


def scripted(base, sub=None, sup=None):
    if sub is None and sup is None:
        return math_run(base)
    node = OxmlElement("m:sSubSup" if sub is not None and sup is not None else ("m:sSub" if sub is not None else "m:sSup"))
    e = OxmlElement("m:e")
    e.append(math_run(base))
    node.append(e)
    for tag, value in (("m:sub", sub), ("m:sup", sup)):
        if value is not None:
            child = OxmlElement(tag)
            child.append(math_run(value))
            node.append(child)
    return node


def equation(doc, number):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(10)
    m = OxmlElement("m:oMath")
    seq = {
        1: [scripted("T", sup="*"), " = P(T) ∪ C(T)."],
        2: [scripted("E", "L"), " = ", scripted("T", "L", "*"), " ∖ ", scripted("T", "O", "*"),
            ";    ", scripted("E", "R"), " = ", scripted("T", "R", "*"), " ∖ ", scripted("T", "O", "*"), "."],
        3: ["D = ", scripted("T", "O", "*"), " ∪ ", scripted("E", "L"), " ∪ ", scripted("E", "R"), " = ",
            scripted("T", "O", "*"), " ∪ ", scripted("T", "L", "*"), " ∪ ", scripted("T", "R", "*"), "."],
        4: ["(Δ ∖ ", scripted("T", "m", "*"), ") ∩ (", scripted("E", "L"), " ∪ ", scripted("E", "R"), ") = ∅."],
    }[number]
    for token in seq:
        m.append(math_run(token) if isinstance(token, str) else token)
    p._p.append(m)
    p.add_run(f"     ({number})")


def table(doc, rows):
    n = len(rows[0])
    widths = {3: [0.62, 4.2, 1.98], 5: [1.15, .8, .92, 1.93, 2.0], 2: [1.0, 5.8]}[n]
    if rows[0][0] == "Period":
        widths = [1.2, 3.2, 2.4]
    t = doc.add_table(rows=1, cols=n)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for col, width in zip(t.columns, widths):
        col.width = Inches(width)
    props = t._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = OxmlElement(f"w:{edge}")
        for key, val in (("val", "single"), ("sz", "4"), ("color", "D9D9D9")):
            element.set(qn(f"w:{key}"), val)
        borders.append(element)
    props.append(borders)
    for i, fields in enumerate(rows):
        cells = t.rows[0].cells if i == 0 else t.add_row().cells
        rowpr = t.rows[i]._tr.get_or_add_trPr()
        rowpr.append(OxmlElement("w:cantSplit"))
        if i == 0:
            repeat = OxmlElement("w:tblHeader")
            rowpr.append(repeat)
        for j, value in enumerate(fields):
            cell = cells[j]
            cell.width = Inches(widths[j])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            tcpr = cell._tc.get_or_add_tcPr()
            margins = OxmlElement("w:tcMar")
            for side, value_twips in (("top", "60"), ("bottom", "60"), ("left", "90"), ("right", "90")):
                margin = OxmlElement(f"w:{side}")
                margin.set(qn("w:w"), value_twips)
                margin.set(qn("w:type"), "dxa")
                margins.append(margin)
            tcpr.append(margins)
            if i == 0:
                shading = OxmlElement("w:shd")
                shading.set(qn("w:fill"), "E9EFF3")
                tcpr.append(shading)
            p = cell.paragraphs[0]
            p.style = "Table Text"
            p.paragraph_format.keep_with_next = i < min(3, len(rows) - 1)
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if n == 5 or j == 0 else WD_ALIGN_PARAGRAPH.LEFT
            inline(p, value)
            if i == 0:
                for r in p.runs:
                    r.bold = True
    after = doc.add_paragraph()
    after.paragraph_format.space_after = Pt(2)
    after.paragraph_format.space_before = Pt(0)
    after.paragraph_format.line_spacing = .4
    after.add_run().font.size = Pt(2)


def build_docx():
    doc = Document()
    section = doc.sections[0]
    section.page_width, section.page_height = Inches(8.5), Inches(11)
    section.top_margin, section.bottom_margin = Inches(.8), Inches(.8)
    section.left_margin, section.right_margin = Inches(.85), Inches(.85)
    section.footer_distance = Inches(.35)
    styles = doc.styles
    # The bundled default may carry a title rule; this manuscript uses typography only.
    for border in list(styles.element.iter(qn("w:pBdr"))):
        border.getparent().remove(border)
    set_font(styles["Normal"], size=11)
    styles["Normal"].paragraph_format.line_spacing = 1.15
    styles["Normal"].paragraph_format.space_after = Pt(6)
    styles["Normal"].paragraph_format.widow_control = True
    set_font(styles["Title"], size=20, bold=True)
    styles["Title"].paragraph_format.space_after = Pt(15)
    for name, size in (("Heading 1", 14), ("Heading 2", 11.5)):
        set_font(styles[name], size=size, bold=True)
        styles[name].paragraph_format.space_before = Pt(14 if name == "Heading 1" else 10)
        styles[name].paragraph_format.space_after = Pt(7)
        styles[name].paragraph_format.keep_with_next = True
    for name, size in (("Caption", 10), ("Table Text", 10), ("Code", 9)):
        if name not in styles:
            styles.add_style(name, 1)
        set_font(styles[name], name="Courier New" if name == "Code" else "Times New Roman", size=size)
        styles[name].paragraph_format.space_after = Pt(4)
        styles[name].paragraph_format.line_spacing = 1.1
    styles["Caption"].font.italic = True
    styles["Table Text"].paragraph_format.space_after = Pt(2)
    styles["Caption"].paragraph_format.space_after = Pt(9)
    styles["Code"].paragraph_format.keep_with_next = True
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    field = OxmlElement("w:fldSimple")
    field.set(qn("w:instr"), "PAGE")
    footer._p.append(field)
    doc.core_properties.title = "Three Way Merging of IDML Documents through Structural Reconciliation and Layered Validation"
    doc.core_properties.subject = "An implementation and empirical case study of structural XML and IDML merging"
    doc.core_properties.author = ""
    doc.core_properties.keywords = "three-way merge, XML, IDML, PCS, validation"
    doc.core_properties.comments = ""
    lines = (HERE / "three-way-merge-paper-en.md").read_text().splitlines()
    i, equation_number, in_code = 0, 0, False
    while i < len(lines):
        line = lines[i].strip()
        i += 1
        if line.startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            doc.add_paragraph(line, "Code")
            continue
        if not line:
            continue
        if line.startswith("# "):
            p = doc.add_paragraph(line[2:], "Title")
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        elif line.startswith("### "):
            doc.add_paragraph(line[4:], "Heading 2")
        elif line.startswith("## "):
            doc.add_paragraph(line[3:], "Heading 1")
        elif line.startswith("$$"):
            equation_number += 1
            equation(doc, equation_number)
        elif line.startswith("!["):
            match = re.fullmatch(r"!\[([^\]]+)\]\(([^\)]+)\)", line)
            p = doc.add_paragraph()
            p.paragraph_format.keep_with_next = True
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(3)
            shape = p.add_run().add_picture(str(HERE / match[2]), width=Inches(6.8))
            shape._inline.docPr.set("descr", match[1])
        elif line.startswith("|"):
            block = [line]
            while i < len(lines) and lines[i].strip().startswith("|"):
                block.append(lines[i].strip())
                i += 1
            rows = [[c.strip() for c in row.strip("|").split("|")] for row in block if not re.fullmatch(r"[| :\-]+", row)]
            table(doc, rows)
        elif line.startswith("*Table ") or line.startswith("*Figure "):
            p = doc.add_paragraph(style="Caption")
            inline(p, line)
            p.paragraph_format.keep_with_next = line.startswith("*Table ")
        elif line.startswith("- "):
            p = doc.add_paragraph(style="List Bullet")
            inline(p, line[2:])
        else:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
            inline(p, line)
    assert equation_number == 4
    doc.save(OUTPUT)
    print(f"Created {OUTPUT}")


if __name__ == "__main__":
    figures_and_statistics()
    build_docx()
