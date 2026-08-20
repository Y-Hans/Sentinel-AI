from datetime import date

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import (
    Flowable,
    Image,
    ListFlowable,
    ListItem,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


OUTPUT_FILE = "Sentinel_AI_Final_Report.pdf"


class ArchitectureDiagram(Flowable):
    def __init__(self, width=450, height=390):
        super().__init__()
        self.width = width
        self.height = height

    def draw_box(self, canvas, x, y, w, h, text, fill, stroke=colors.HexColor("#23424A")):
        canvas.setStrokeColor(stroke)
        canvas.setFillColor(fill)
        canvas.roundRect(x, y, w, h, 6, stroke=1, fill=1)
        canvas.setFillColor(colors.HexColor("#102027"))
        canvas.setFont("Helvetica-Bold", 8.5)
        lines = text.split("\n")
        start_y = y + h / 2 + (len(lines) - 1) * 5
        for i, line in enumerate(lines):
            canvas.drawCentredString(x + w / 2, start_y - i * 10, line)

    def arrow(self, canvas, x1, y1, x2, y2):
        canvas.setStrokeColor(colors.HexColor("#607D8B"))
        canvas.setLineWidth(1.2)
        canvas.line(x1, y1, x2, y2)
        dx = 5 if x2 >= x1 else -5
        canvas.line(x2, y2, x2 - dx, y2 + 3)
        canvas.line(x2, y2, x2 - dx, y2 - 3)

    def draw(self):
        c = self.canv
        c.saveState()
        x0 = 20
        y_top = 350
        box_w = 120
        box_h = 42
        fill_a = colors.HexColor("#E7F3F1")
        fill_b = colors.HexColor("#FFF4D6")
        fill_c = colors.HexColor("#F3E9FF")
        fill_d = colors.HexColor("#E9EEF7")

        self.draw_box(c, x0 + 150, y_top, 150, box_h, "Android Entry Points\nLinks, shares, text, notifications", fill_d)
        self.draw_box(c, x0 + 150, y_top - 66, 150, box_h, "Input Routing\nNormalize and classify payload", fill_a)
        self.arrow(c, x0 + 225, y_top, x0 + 225, y_top - 24)

        self.draw_box(c, x0 + 35, y_top - 136, box_w, box_h, "URL/File Protection\nHeuristics + reputation", fill_b)
        self.draw_box(c, x0 + 165, y_top - 136, box_w, box_h, "ML Inference\n15 features + TFLite", fill_c)
        self.draw_box(c, x0 + 295, y_top - 136, box_w, box_h, "Message Protection\nScam rules", fill_b)

        self.arrow(c, x0 + 195, y_top - 66, x0 + 95, y_top - 94)
        self.arrow(c, x0 + 225, y_top - 66, x0 + 225, y_top - 94)
        self.arrow(c, x0 + 255, y_top - 66, x0 + 355, y_top - 94)

        self.draw_box(c, x0 + 150, y_top - 206, 150, box_h, "Decision Engine\nALLOW / WARN / BLOCK", colors.HexColor("#E8F5E9"))
        self.arrow(c, x0 + 95, y_top - 136, x0 + 195, y_top - 164)
        self.arrow(c, x0 + 225, y_top - 136, x0 + 225, y_top - 164)
        self.arrow(c, x0 + 355, y_top - 136, x0 + 255, y_top - 164)

        self.draw_box(c, x0 + 70, y_top - 276, 130, box_h, "User Warning UI\nExplanations", colors.HexColor("#FDECEC"))
        self.draw_box(c, x0 + 250, y_top - 276, 130, box_h, "Threat Journal\nRoom history", colors.HexColor("#EDF2FA"))
        self.arrow(c, x0 + 200, y_top - 206, x0 + 135, y_top - 234)
        self.arrow(c, x0 + 250, y_top - 206, x0 + 315, y_top - 234)

        c.restoreState()


def p(text, style):
    return Paragraph(text, style)


def bullets(items, style):
    return ListFlowable(
        [ListItem(Paragraph(item, style), leftIndent=12) for item in items],
        bulletType="bullet",
        start="circle",
        leftIndent=16,
        bulletFontName="Helvetica",
        bulletFontSize=7,
    )


def add_section(story, title, body, styles):
    story.append(Paragraph(title, styles["Heading1"]))
    story.append(Spacer(1, 6))
    if isinstance(body, list):
        for item in body:
            story.append(item)
            story.append(Spacer(1, 6))
    else:
        story.append(Paragraph(body, styles["Body"]))
    story.append(Spacer(1, 12))


def table(data, col_widths=None):
    t = Table(data, colWidths=col_widths, hAlign="LEFT")
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1F4E5F")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
                ("FONTSIZE", (0, 0), (-1, -1), 8.5),
                ("ALIGN", (0, 0), (-1, -1), "LEFT"),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#B0BEC5")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F7FAFC")]),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    return t


def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#D5DEE3"))
    canvas.line(doc.leftMargin, 0.55 * inch, A4[0] - doc.rightMargin, 0.55 * inch)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#607D8B"))
    canvas.drawString(doc.leftMargin, 0.35 * inch, "Sentinel AI - Real-Time Scam Protection System")
    canvas.drawRightString(A4[0] - doc.rightMargin, 0.35 * inch, f"Page {doc.page}")
    canvas.restoreState()


def build():
    doc = SimpleDocTemplate(
        OUTPUT_FILE,
        pagesize=A4,
        rightMargin=0.72 * inch,
        leftMargin=0.72 * inch,
        topMargin=0.72 * inch,
        bottomMargin=0.72 * inch,
        title="Sentinel AI Final Project Report",
        author="Sentinel AI Project Team",
    )

    base = getSampleStyleSheet()
    styles = {
        "Title": ParagraphStyle(
            "Title",
            parent=base["Title"],
            fontName="Helvetica-Bold",
            fontSize=29,
            leading=34,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#183A47"),
            spaceAfter=12,
        ),
        "Subtitle": ParagraphStyle(
            "Subtitle",
            parent=base["Normal"],
            fontName="Helvetica",
            fontSize=15,
            leading=20,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#355A66"),
            spaceAfter=28,
        ),
        "Heading1": ParagraphStyle(
            "Heading1",
            parent=base["Heading1"],
            fontName="Helvetica-Bold",
            fontSize=15,
            leading=19,
            textColor=colors.HexColor("#1F4E5F"),
            spaceBefore=10,
            spaceAfter=4,
        ),
        "Heading2": ParagraphStyle(
            "Heading2",
            parent=base["Heading2"],
            fontName="Helvetica-Bold",
            fontSize=11.5,
            leading=14,
            textColor=colors.HexColor("#263238"),
            spaceBefore=8,
            spaceAfter=4,
        ),
        "Body": ParagraphStyle(
            "Body",
            parent=base["BodyText"],
            fontName="Helvetica",
            fontSize=9.7,
            leading=14.2,
            alignment=TA_JUSTIFY,
            spaceAfter=5,
        ),
        "Bullet": ParagraphStyle(
            "Bullet",
            parent=base["BodyText"],
            fontName="Helvetica",
            fontSize=9.4,
            leading=13.5,
            alignment=TA_LEFT,
        ),
        "Small": ParagraphStyle(
            "Small",
            parent=base["BodyText"],
            fontName="Helvetica",
            fontSize=8.5,
            leading=11.5,
            textColor=colors.HexColor("#455A64"),
        ),
        "Center": ParagraphStyle(
            "Center",
            parent=base["BodyText"],
            fontName="Helvetica",
            fontSize=10,
            leading=14,
            alignment=TA_CENTER,
        ),
    }

    story = []

    story.append(Spacer(1, 1.05 * inch))
    story.append(Paragraph("Sentinel AI", styles["Title"]))
    story.append(Paragraph("Real-Time Scam Protection System", styles["Subtitle"]))
    story.append(Spacer(1, 0.35 * inch))
    cover_data = [
        ["Prepared by", "Sentinel AI Project Team"],
        ["Project Type", "Android-based real-time phishing and scam protection system"],
        ["Primary Platform", "Android 8.0 (API 26) and later"],
        ["Date", date.today().strftime("%B %d, %Y")],
    ]
    story.append(table(cover_data, [1.8 * inch, 4.1 * inch]))
    story.append(Spacer(1, 0.45 * inch))
    story.append(
        Paragraph(
            "A technical project report covering system design, machine learning workflow, implementation, privacy model, evaluation, challenges, and future development scope.",
            styles["Center"],
        )
    )
    story.append(PageBreak())

    add_section(
        story,
        "Abstract",
        "Sentinel AI is a real-time scam protection system designed to reduce phishing and social-engineering risk at the moment a user is most vulnerable: before opening a suspicious link or responding to a deceptive notification. Phishing attacks commonly imitate trusted institutions, hide unsafe destinations, and use urgency to pressure users into sharing credentials, payment details, or personal information. Sentinel AI addresses this problem through an Android-first, local protection pipeline that intercepts links, normalizes input, extracts risk signals, runs explainable heuristic checks, and applies an on-device TensorFlow Lite URL classifier. The system combines evidence into an understandable ALLOW, WARN, or BLOCK decision with a risk score and user-facing reasons. Its key innovation is click-time prevention with offline machine learning and privacy-preserving analysis: URL features, notification content, model inference, preferences, and scan history remain on the device by default. The result is a practical defense layer that helps users make safer decisions without relying on a cloud backend or exposing sensitive content.",
        styles,
    )

    add_section(
        story,
        "1. Introduction",
        [
            p("Phishing and digital scam attacks have become one of the most common ways users lose account access, money, and personal data. Unlike attacks that depend entirely on technical software vulnerabilities, phishing frequently succeeds by manipulating human behavior. Attackers create realistic messages, impersonate banks or public agencies, and direct users to fraudulent pages that request passwords, one-time codes, payment details, identity documents, or wallet credentials.", styles["Body"]),
            p("Mobile users face this risk in a compressed decision environment. Links arrive through messaging apps, email clients, social platforms, browser redirects, and notifications. The destination is often shortened, encoded, or visually similar to a trusted brand. Existing protections may warn after a site is loaded, depend on external blocklists, or fail when a new phishing campaign has not yet been reported. A real-time prevention layer is therefore needed before navigation occurs.", styles["Body"]),
            p("Sentinel AI is built around this prevention-first idea. It adds a decision point between user intent and browser handoff, while also scanning supported notifications for scam-like language and unsafe URL patterns. The system is intended to convert technical risk signals into clear action guidance.", styles["Body"]),
        ],
        styles,
    )

    add_section(
        story,
        "2. Problem Statement",
        [
            p("Users are regularly exposed to links and messages that imitate legitimate organizations and attempt to steal credentials, payments, or personal information. The problem is important because a single unsafe click can lead to financial loss, account takeover, identity exposure, malicious downloads, and continued impersonation of trusted contacts.", styles["Body"]),
            p("Existing protection methods have several limitations. Browser warnings may appear only after navigation has started. Server-side reputation services can miss newly created phishing domains and may require sharing the URL externally. General antivirus tools often focus on files rather than intent-time link decisions. Manual user judgment is unreliable when the attacker uses urgency, brand imitation, shortened URLs, raw IP addresses, or familiar sender names.", styles["Body"]),
            p("The central problem addressed by Sentinel AI is how to assess a link or notification quickly, privately, and explainably before the user proceeds with a risky action.", styles["Body"]),
        ],
        styles,
    )

    add_section(
        story,
        "3. Proposed Solution",
        [
            p("Sentinel AI proposes a local-first Android protection pipeline. When a user opens, shares, selects, or manually scans a URL-like input, the app captures the payload, normalizes it, analyzes it, and returns a decision before the browser or target application receives the link. For supported notifications, the app locally evaluates message text, extracted URLs, urgency indicators, credential-related terms, financial language, sender context, and duplicate events.", styles["Body"]),
            p("The decision system is risk-based. It does not expose raw technical features as the final user experience. Instead, it combines URL heuristics, optional reputation evidence, and on-device model output into a score and action. ALLOW indicates no strong local or provider evidence, WARN indicates meaningful suspicious signals, and BLOCK indicates a critical local score or malicious reputation evidence.", styles["Body"]),
            p("This design creates a practical middle layer between user intent and unsafe action. It remains usable offline for core protection and does not require a Sentinel AI backend.", styles["Body"]),
        ],
        styles,
    )

    story.append(Paragraph("4. System Architecture", styles["Heading1"]))
    story.append(p("Sentinel AI is structured as a multi-module Android application. The app module handles startup, intent routing, URL and file orchestration, machine learning inference, reputation integration, and warnings. The core module defines domain models, validation, event handling, Room storage, networking contracts, and feature state. The agents module performs notification parsing, supported-app routing, message event construction, and scam rules. Services run guard and monitoring work, while the UI module contains Compose screens, navigation, view models, scanner, dashboard, alert, detail, settings, and history interfaces.", styles["Body"]))
    story.append(Spacer(1, 8))
    story.append(ArchitectureDiagram())
    story.append(Spacer(1, 8))
    story.append(p("The architecture begins with Android entry points such as web intents, share intents, selected text, manual scanner input, and notification listener events. Inputs are routed and normalized before moving into either URL/file protection or message protection. URL protection applies local heuristics, optional reputation checks, and the TensorFlow Lite model. Message protection applies notification-specific parsing and scam rules. Results flow through a threat event bus to the warning UI and local threat journal.", styles["Body"]))
    story.append(p("The ML inference flow transforms a normalized URL into 15 structural and lexical features, standardizes them with bundled scaler values, executes a float TensorFlow Lite model with input shape [1, 15], and produces a phishing probability. The evidence layer remains responsible for the final ALLOW, WARN, or BLOCK action, while the model contributes to the reported score.", styles["Body"]))
    story.append(Spacer(1, 12))

    story.append(Paragraph("5. Machine Learning Model", styles["Heading1"]))
    story.append(p("The machine learning component estimates phishing probability from engineered URL features. It complements the heuristic and reputation layers rather than replacing them. The training corpus described by the project contains more than 238,000 labeled URLs for binary classification of benign and phishing-like URLs. The deployed Android artifact is model.tflite, packaged with a matching scaler.json file.", styles["Body"]))
    feature_rows = [["No.", "Feature", "Purpose"]]
    features = [
        ("1", "URLLength", "Total normalized URL length"),
        ("2", "DomainLength", "Hostname length"),
        ("3", "IsDomainIP", "Detects raw IPv4 hostnames"),
        ("4", "NoOfSubDomain", "Counts extra hostname labels"),
        ("5", "IsHTTPS", "Identifies HTTPS scheme usage"),
        ("6", "HasSuspiciousWords", "Flags terms such as login, verify, account, or crypto"),
        ("7", "SpecialCharRatio", "Measures non-alphanumeric character density"),
        ("8", "DigitRatio", "Measures digit density"),
        ("9", "HasAtSymbol", "Detects @ symbols in URLs"),
        ("10", "SuspiciousTLD", "Flags configured suspicious top-level labels"),
        ("11", "BrandImpersonationScore", "Combines brand, suspicious-word, and hyphen signals"),
        ("12", "HyphenCount", "Counts hyphens in the URL"),
        ("13", "PathQueryLength", "Measures combined path and query length"),
        ("14", "KnownBrandDomain", "Checks official brand-domain matches"),
        ("15", "DomainVowelRatio", "Measures vowel density in hostname"),
    ]
    feature_rows += features
    story.append(table(feature_rows, [0.45 * inch, 1.65 * inch, 3.95 * inch]))
    story.append(Spacer(1, 8))
    story.append(p("The training approach follows a standard TensorFlow pipeline: clean and label URLs, extract the same 15 features used by Android, fit feature means and scales on the training partition, train a binary classifier using held-out validation and test partitions, export the trained model to TensorFlow Lite, and package the model with matching scaler values. At runtime, Android validates the expected model input and output tensor shapes to prevent incompatible assets from being used silently.", styles["Body"]))
    story.append(p("The reported score blends model probability with evidence-based scoring. The model probability is converted to a percentage, boosted, clamped, and combined using a 70 percent evidence score and 30 percent boosted ML score formula. This keeps the model influential while preserving rule-based blocking for clear high-risk evidence.", styles["Body"]))
    pred_data = [
        ["Sample URL", "Model phishing probability", "Interpretation"],
        ["google.com", "0.00002", "Safe or near-zero risk signal"],
        ["shipaton.com", "0.07", "Low but non-zero suspicious signal"],
        ["secure-login-google.xyz", "1.00", "High-confidence phishing-like signal"],
        ["example.com/login", "0.49", "Mid-range suspicious signal requiring other evidence"],
    ]
    story.append(table(pred_data, [2.45 * inch, 1.55 * inch, 2.05 * inch]))
    story.append(Spacer(1, 12))

    add_section(
        story,
        "6. Features of the System",
        [
            bullets(
                [
                    "<b>Click-time protection:</b> Intercepts HTTP and HTTPS links before browser handoff and can block unsafe navigation.",
                    "<b>Notification scanning:</b> Reviews supported messaging, email, and social notifications for URLs, urgency, financial language, credential requests, and sender context.",
                    "<b>Risk scoring:</b> Converts local and provider evidence into GREEN, YELLOW, RED, or CRITICAL score bands and ALLOW, WARN, or BLOCK actions.",
                    "<b>Manual scanning:</b> Provides in-app scanning for links, text, and supported file references.",
                    "<b>Offline functionality:</b> Local heuristics, notification rules, file heuristics, and TFLite inference continue without cloud services.",
                    "<b>Privacy-first design:</b> Scan history, preferences, URL features, and message analysis remain on the device by default.",
                    "<b>Local history:</b> Threat events are stored in a private Room database and shown in dashboard, alert, detail, and history views.",
                ],
                styles["Bullet"],
            )
        ],
        styles,
    )

    add_section(
        story,
        "7. Implementation Details",
        [
            p("The Android implementation uses intent filters to receive web-open actions, shared links, selected URL-like text, and supported file references. IntentRouterActivity checks whether click protection is enabled, identifies the incoming payload, and routes it toward loading and scanning screens. ScanRepository coordinates heuristic analysis, reputation evidence, and ML inference before a result is displayed.", styles["Body"]),
            p("The ML path is managed through bundled application assets. model.tflite contains the TensorFlow Lite classifier, while scaler.json stores the feature means and scales required for standardization. The app extracts raw URL features in the documented order, transforms them into a Float32 tensor, invokes the interpreter, and blends the resulting probability with evidence-based scoring.", styles["Body"]),
            p("For notifications, SentinelNotificationListener receives events only after the user grants notification-listener access. NotificationAgentCoordinator parses the sender and message text, normalizes the event, extracts URL and content signals, suppresses near-duplicate notifications, and emits elevated events to the warning notification path and local journal.", styles["Body"]),
            p("The application uses Hilt for dependency injection, coroutines and flows for asynchronous state, and Room for persistent local threat history. Feature toggles are stored in private Android preferences and include real-time protection, notification protection, click protection, and text-selection analysis.", styles["Body"]),
        ],
        styles,
    )

    add_section(
        story,
        "8. Privacy and Security Considerations",
        [
            p("Sentinel AI is designed so the primary detection paths run on the Android device. In the default repository configuration, message content, file content, URL feature vectors, and scan history are not sent to a Sentinel AI backend. The app does not require an account or cloud inference service for its core protection.", styles["Body"]),
            p("The TensorFlow Lite model and scaler are packaged with the application, which enables offline inference, low latency, a fixed model version per build, and reduced exposure of browsing and message data. Scan records are stored in the app private Room database, while preferences are stored in private Android preferences.", styles["Body"]),
            p("The repository supports optional reputation integrations. OpenPhish can download a feed and compare scanned URLs locally; VirusTotal remains disabled unless a developer supplies an API key. If VirusTotal is enabled, scanned URLs are submitted externally and that configuration must be disclosed. For strict offline use, external reputation providers can be disabled while retaining local heuristics and ML inference.", styles["Body"]),
        ],
        styles,
    )

    add_section(
        story,
        "9. Challenges Faced",
        [
            bullets(
                [
                    "<b>Dataset balancing:</b> Phishing and benign URL datasets must be balanced carefully so the model does not overfit one class or produce misleading confidence.",
                    "<b>Feature extraction consistency:</b> The Android app and training pipeline must preserve the exact same 15-feature order, data types, and scaler values.",
                    "<b>Malformed URL handling:</b> The runtime must generate a finite feature vector even when inputs are incomplete, unusual, or intentionally malformed.",
                    "<b>Real-time performance:</b> Click-time protection must analyze a link quickly enough that the user experience remains smooth.",
                    "<b>Android ML integration:</b> The TFLite model, scaler, tensor shapes, asset loading, and lifecycle management must work reliably across devices.",
                    "<b>Privacy boundaries:</b> Optional reputation checks must be clearly separated from offline inference so user data handling remains transparent.",
                ],
                styles["Bullet"],
            )
        ],
        styles,
    )

    add_section(
        story,
        "10. Results and Evaluation",
        [
            p("The project demonstrates a layered protection system that can evaluate URLs at click time, scan supported notifications, produce risk scores, and keep history locally. The example model predictions show near-zero probability for clearly safe domains, mid-range values for ambiguous login paths, and high confidence for brand-impersonation style phishing domains.", styles["Body"]),
            p("The project target and reported demonstration accuracy are approximately 99 percent for the URL classification task. Because the repository contains the deployed inference model and scaler but not the original training dataset, training scripts, or versioned evaluation report, this figure should be treated as a project-level reported result rather than a reproducible benchmark from the current repository alone.", styles["Body"]),
            p("Model reliability is strengthened by using the classifier as one part of a layered decision system. Heuristic rules and provider evidence can still warn or block even when the model score is inconclusive, while unknown or failed reputation lookups do not reduce local risk. This avoids presenting missing evidence as safety.", styles["Body"]),
        ],
        styles,
    )

    add_section(
        story,
        "11. Future Scope",
        [
            bullets(
                [
                    "Evaluate lightweight deep-learning and sequence models for more complex URL patterns while preserving on-device latency.",
                    "Expand scam-text detection for Indian languages and transliterated message content.",
                    "Add browser integrations for Chrome, Edge, Firefox, and other navigation surfaces.",
                    "Extend support to iOS and desktop platforms where platform APIs permit similar protection flows.",
                    "Add domain-age, certificate, redirect-chain, lookalike-domain, internationalized-domain, QR-code, and document-link signals.",
                    "Introduce versioned model evaluation reports with accuracy, precision, recall, F1-score, latency, and model-size comparisons.",
                ],
                styles["Bullet"],
            )
        ],
        styles,
    )

    add_section(
        story,
        "12. Conclusion",
        [
            p("Sentinel AI achieves a practical real-time defense layer against phishing and scam attempts on Android. It intercepts risky user actions before navigation, analyzes links and notifications locally, applies both explainable rules and on-device machine learning, and presents simple decisions that users can act on immediately.", styles["Body"]),
            p("The project matters because phishing prevention is most effective before a user enters credentials, sends money, or opens a malicious destination. By combining click-time interception, local inference, risk scoring, notification analysis, and privacy-first storage, Sentinel AI demonstrates a submission-ready approach to safer mobile interaction without depending on a central backend.", styles["Body"]),
        ],
        styles,
    )

    doc.build(story, onFirstPage=footer, onLaterPages=footer)


if __name__ == "__main__":
    build()
