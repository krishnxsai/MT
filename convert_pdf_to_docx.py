#!/usr/bin/env python3
"""
Convert PDF to DOCX using PyPDF2 and python-docx
"""
import sys
try:
    import PyPDF2
except:
    print("Installing PyPDF2...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "PyPDF2", "-q"])
    import PyPDF2

from docx import Document
from docx.shared import Pt, Inches
from docx.enum.text import WD_PARAGRAPH_ALIGNMENT

def pdf_to_docx(pdf_path, docx_path):
    """Convert PDF to DOCX"""
    print(f"Reading PDF: {pdf_path}")

    # Extract text from PDF
    text_content = []
    with open(pdf_path, 'rb') as f:
        reader = PyPDF2.PdfReader(f)
        print(f"Total pages: {len(reader.pages)}")
        for page_num, page in enumerate(reader.pages):
            text = page.extract_text()
            text_content.append(text)
            print(f"  Extracted page {page_num + 1}")

    # Create DOCX document
    print(f"\nCreating DOCX: {docx_path}")
    doc = Document()

    # Add content
    for page_num, text in enumerate(text_content):
        if text.strip():
            # Add page content
            paragraphs = text.split('\n')
            for para_text in paragraphs:
                if para_text.strip():
                    p = doc.add_paragraph(para_text)
                    # Set font
                    for run in p.runs:
                        run.font.size = Pt(11)

    # Save document
    doc.save(docx_path)
    print(f"✓ Successfully created: {docx_path}")

if __name__ == '__main__':
    pdf_file = r"c:/Users/wujjw/Music/MediTrack-main/MediTrack-main/alternate_tj_latex_template_ap/meditrack_paper.pdf"
    docx_file = r"c:/Users/wujjw/Music/MediTrack-main/MediTrack-main/alternate_tj_latex_template_ap/meditrack_paper.docx"

    pdf_to_docx(pdf_file, docx_file)
