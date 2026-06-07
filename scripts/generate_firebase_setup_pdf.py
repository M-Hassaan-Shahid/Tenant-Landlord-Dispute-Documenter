#!/usr/bin/env python3
"""Generate ProofNest Firebase dashboard setup PDF."""

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import (
    ListFlowable,
    ListItem,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

PROJECT_ID = "testproject-a9929"
OUTPUT = Path(__file__).resolve().parents[1] / "docs" / "FIREBASE_DASHBOARD_SETUP.pdf"


def build_pdf(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(path),
        pagesize=letter,
        leftMargin=0.75 * inch,
        rightMargin=0.75 * inch,
        topMargin=0.75 * inch,
        bottomMargin=0.75 * inch,
    )
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "Title",
        parent=styles["Heading1"],
        fontSize=20,
        spaceAfter=12,
        textColor=colors.HexColor("#1B5E4B"),
    )
    h2 = ParagraphStyle(
        "H2",
        parent=styles["Heading2"],
        fontSize=14,
        spaceBefore=14,
        spaceAfter=8,
        textColor=colors.HexColor("#1B5E4B"),
    )
    body = ParagraphStyle(
        "Body",
        parent=styles["Normal"],
        fontSize=10,
        leading=14,
        spaceAfter=6,
    )
    mono = ParagraphStyle(
        "Mono",
        parent=styles["Code"],
        fontSize=8.5,
        leading=11,
        backColor=colors.HexColor("#F4F6F5"),
        borderPadding=6,
        spaceAfter=8,
    )
    note = ParagraphStyle(
        "Note",
        parent=body,
        textColor=colors.HexColor("#5C5C5C"),
        fontSize=9,
        leftIndent=12,
    )

    story = []
    story.append(Paragraph("ProofNest — Firebase &amp; Cloudinary Setup", title))
    story.append(
        Paragraph(
            f"Dashboard-only guide (no local CLI). Firebase project: <b>{PROJECT_ID}</b>",
            body,
        )
    )
    story.append(Spacer(1, 0.15 * inch))

    def section(title_text: str, bullets: list[str]) -> None:
        story.append(Paragraph(title_text, h2))
        items = [ListItem(Paragraph(b, body), leftIndent=12) for b in bullets]
        story.append(ListFlowable(items, bulletType="bullet", start="•"))

    def numbered(title_text: str, steps: list[str]) -> None:
        story.append(Paragraph(title_text, h2))
        for i, step in enumerate(steps, 1):
            story.append(Paragraph(f"<b>{i}.</b> {step}", body))

    # Part 1
    numbered(
        "Part 1 — Get Cloudinary credentials",
        [
            'Open <font color="#1565C0">https://console.cloudinary.com</font> and sign in.',
            "On the Dashboard home, note your <b>Cloud name</b> (e.g. dspdmxdaq), "
            "<b>API Key</b>, and <b>API Secret</b> (click Reveal). Keep the secret private.",
        ],
    )
    table_data = [
        ["Firebase env variable", "Value"],
        ["CLOUDINARY_CLOUD_NAME", "e.g. dspdmxdaq"],
        ["CLOUDINARY_API_KEY", "From Cloudinary dashboard"],
        ["CLOUDINARY_API_SECRET", "From Cloudinary dashboard (secret)"],
    ]
    t = Table(table_data, colWidths=[2.4 * inch, 3.6 * inch])
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1B5E4B")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTSIZE", (0, 0), (-1, -1), 9),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAF9")]),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    story.append(Spacer(1, 0.1 * inch))
    story.append(t)
    story.append(Spacer(1, 0.1 * inch))

    # Part 2
    numbered(
        "Part 2 — Upgrade Firebase to Blaze",
        [
            f'Open Firebase Console → project <b>{PROJECT_ID}</b>.',
            "Bottom left: Upgrade → choose <b>Blaze (pay as you go)</b>. "
            "Cloud Functions and outbound networking require Blaze.",
        ],
    )

    # Part 3
    story.append(Paragraph("Part 3 — Set Cloudinary environment variables", h2))
    story.append(
        Paragraph(
            "Your functions use <b>process.env</b> (Functions v2). "
            "Do <b>not</b> use the old <i>firebase functions:config:set</i> command.",
            body,
        )
    )
    story.append(Paragraph("<b>Option A — Firebase Console (per function)</b>", body))
    section(
        "",
        [
            f"Go to Firebase → Build → Functions (project {PROJECT_ID}).",
            "After functions are deployed, open each function: "
            "<b>getCloudinaryUploadParams</b> and <b>sendNotification</b>.",
            "Open Configuration / Environment variables.",
            "Add CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET.",
            "Save and wait for the new revision to deploy.",
        ],
    )
    story.append(Paragraph("<b>Option B — Google Cloud Console</b>", body))
    section(
        "",
        [
            f"Open Cloud Functions list for project {PROJECT_ID}.",
            "Edit getCloudinaryUploadParams → Runtime environment variables → add the three vars → Deploy.",
            "Only getCloudinaryUploadParams strictly needs Cloudinary vars.",
        ],
    )
    story.append(Paragraph("<b>Optional — Secret Manager (safer for API secret)</b>", body))
    section(
        "",
        [
            "Google Cloud → Security → Secret Manager → Create secret CLOUDINARY_API_SECRET.",
            "In the function edit screen → Secrets → mount as env var CLOUDINARY_API_SECRET.",
        ],
    )

    # Part 4
    numbered(
        "Part 4 — Deploy Firestore rules",
        [
            f"Firebase Console → Firestore → Rules (project {PROJECT_ID}).",
            "In your repo, open <b>firestore.rules</b> (project root), copy all contents.",
            "Paste into the Firebase Rules editor, replacing existing rules.",
            "Click <b>Publish</b>.",
        ],
    )

    # Part 5
    numbered(
        "Part 5 — Deploy Storage rules",
        [
            f"Firebase Console → Storage → Rules (project {PROJECT_ID}).",
            "If Storage is not enabled: Get started → pick a region.",
            "Open repo file <b>storage.rules</b>, copy all contents into the editor.",
            "Click <b>Publish</b>.",
        ],
    )

    # Part 6
    story.append(Paragraph("Part 6 — Deploy Cloud Functions (browser Cloud Shell)", h2))
    story.append(
        Paragraph(
            "Firebase Console cannot upload <b>firebase/functions/index.js</b> by clicking alone. "
            "Use <b>Cloud Shell</b> in the browser (no local install).",
            body,
        )
    )
    section(
        "",
        [
            f"Firebase Console → project {PROJECT_ID} → Cloud Shell icon (top right).",
            "Clone or upload your project into Cloud Shell.",
            "Run: npm install --prefix firebase/functions",
            "Run: npx firebase-tools login",
            f"Run: npx firebase-tools use {PROJECT_ID}",
            "Run: npx firebase-tools deploy --only functions",
            "Confirm getCloudinaryUploadParams, sendNotification, onNotificationCreated appear under Functions.",
            "Re-apply env vars (Part 3) on the deployed revision if you set them before first deploy.",
        ],
    )
    story.append(Paragraph("Cloud Shell commands (copy as a block):", body))
    story.append(
        Paragraph(
            "npm install --prefix firebase/functions<br/>"
            "npx firebase-tools login<br/>"
            f"npx firebase-tools use {PROJECT_ID}<br/>"
            "npx firebase-tools deploy --only functions",
            mono,
        )
    )

    # Part 7
    numbered(
        "Part 7 — Verify in the app",
        [
            "Rebuild and install the Android app.",
            "Sign in, complete inspection, open Review &amp; Sign, draw signature, tap Confirm signature.",
            "Should succeed without error_notfound.",
            "Optional: upload a checklist photo or profile photo to test Cloudinary signed uploads.",
        ],
    )

    # Checklist
    story.append(Paragraph("Quick checklist", h2))
    checklist = [
        ["Step", "Where", "Done"],
        ["Cloudinary credentials", "console.cloudinary.com", "☐"],
        ["Blaze plan", "Firebase → Upgrade", "☐"],
        ["CLOUDINARY_* env vars", "Firebase / Cloud Functions → Edit", "☐"],
        ["firestore.rules", "Firebase → Firestore → Rules → Publish", "☐"],
        ["storage.rules", "Firebase → Storage → Rules → Publish", "☐"],
        ["Function code deploy", "Cloud Shell → firebase deploy --only functions", "☐"],
    ]
    ct = Table(checklist, colWidths=[2.2 * inch, 2.8 * inch, 0.6 * inch])
    ct.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1B5E4B")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTSIZE", (0, 0), (-1, -1), 9),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAF9")]),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    story.append(ct)
    story.append(Spacer(1, 0.15 * inch))

    story.append(Paragraph("What NOT to use", h2))
    section(
        "",
        [
            "firebase functions:config:set — Gen 1 only; your code uses process.env.CLOUDINARY_*.",
            "Putting API secret in the Android app or google-services.json — server only.",
        ],
    )

    story.append(Spacer(1, 0.2 * inch))
    story.append(
        Paragraph(
            "Useful links:<br/>"
            f"• Firebase project: https://console.firebase.google.com/project/{PROJECT_ID}/overview<br/>"
            f"• Functions: https://console.firebase.google.com/project/{PROJECT_ID}/functions<br/>"
            f"• Firestore rules: https://console.firebase.google.com/project/{PROJECT_ID}/firestore/rules<br/>"
            f"• Storage rules: https://console.firebase.google.com/project/{PROJECT_ID}/storage/rules<br/>"
            "• Cloudinary: https://console.cloudinary.com",
            note,
        )
    )

    doc.build(story)
    print(f"Wrote {path}")


if __name__ == "__main__":
    build_pdf(OUTPUT)
