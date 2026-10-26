package com.memoriesmadevisible.notification;

import java.util.List;

/**
 * Renders the studio's branded HTML email. Email clients are stuck in 2005:
 * tables for layout, inline styles only, no web fonts, no flexbox. Everything
 * here is written for that world, and every email also carries a plain-text
 * alternative so it reads fine in a terminal or a watch notification.
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    /** One label/value row in the details card (e.g. "Date" → "Saturday, November 7, 2026"). */
    public record Detail(String label, String value) {}

    public record Rendered(String html, String text) {}

    // Light-theme brand palette (dark email backgrounds render unpredictably in Outlook/Gmail dark mode).
    private static final String BG = "#f3f3f5";
    private static final String CARD = "#ffffff";
    private static final String INK = "#141518";
    private static final String MUTED = "#5f6b78";
    private static final String ACCENT = "#9a6b22";
    private static final String ACCENT_SOFT = "#f6efe3";
    private static final String HEADER = "#0f1012";
    private static final String FONT = "'Figtree', -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
    private static final String HEADING_FONT = "Georgia, 'Times New Roman', serif";

    /**
     * @param siteUrl    e.g. https://www.memoriesmadevisible.com (for the logo and footer link)
     * @param heading    the big line at the top of the card
     * @param bodyText   paragraphs separated by blank lines; single newlines become line breaks
     * @param details    optional details card rows; empty list hides the card
     * @param ctaLabel   optional button text (null to omit)
     * @param ctaUrl     button link
     * @param footerNote small print under the signature (e.g. "Reply to this email with any questions")
     */
    public static Rendered render(String siteUrl, String heading, String bodyText, List<Detail> details,
                                  String ctaLabel, String ctaUrl, String footerNote) {
        String site = siteUrl == null ? "" : siteUrl.replaceAll("/+$", "");
        StringBuilder h = new StringBuilder(4000);
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<meta name=\"color-scheme\" content=\"light\"><title>").append(esc(heading)).append("</title></head>")
         .append("<body style=\"margin:0;padding:0;background:").append(BG).append(";\">")
         // Preheader: the snippet inbox apps show next to the subject
         .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">").append(esc(firstLine(bodyText))).append("</div>")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:").append(BG).append(";\"><tr><td align=\"center\" style=\"padding:32px 16px;\">")
         .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;width:100%;\">");

        // Header strip with the logo
        h.append("<tr><td style=\"background:").append(HEADER).append(";border-radius:24px 24px 0 0;padding:28px 36px;text-align:left;\">")
         .append("<a href=\"").append(esc(site)).append("/\" style=\"text-decoration:none;\">")
         .append("<img src=\"").append(esc(site)).append("/images/logo-email.png\" width=\"200\" alt=\"Memories Made Visible Photography\" style=\"display:block;width:200px;height:auto;border:0;\">")
         .append("</a></td></tr>");

        // Card
        h.append("<tr><td style=\"background:").append(CARD).append(";border-radius:0 0 24px 24px;padding:36px 36px 32px;font-family:").append(FONT).append(";color:").append(INK).append(";\">")
         .append("<h1 style=\"margin:0 0 20px;font-family:").append(HEADING_FONT).append(";font-size:28px;line-height:1.2;font-weight:400;color:").append(INK).append(";\">").append(esc(heading)).append("</h1>")
         .append(paragraphs(bodyText));

        if (details != null && !details.isEmpty()) {
            h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 8px;background:").append(ACCENT_SOFT).append(";border-radius:18px;\">");
            for (Detail d : details) {
                if (d.value() == null || d.value().isBlank()) continue;
                h.append("<tr><td style=\"padding:10px 20px 0;font-family:").append(FONT).append(";font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:").append(MUTED).append(";\">").append(esc(d.label())).append("</td></tr>")
                 .append("<tr><td style=\"padding:2px 20px 12px;font-family:").append(FONT).append(";font-size:16px;font-weight:600;color:").append(INK).append(";\">").append(esc(d.value())).append("</td></tr>");
            }
            h.append("</table>");
        }

        if (ctaLabel != null && !ctaLabel.isBlank() && ctaUrl != null) {
            h.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 8px;\"><tr><td style=\"background:").append(ACCENT).append(";border-radius:999px;\">")
             .append("<a href=\"").append(esc(ctaUrl)).append("\" style=\"display:inline-block;padding:13px 26px;font-family:").append(FONT).append(";font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:999px;\">").append(esc(ctaLabel)).append("</a>")
             .append("</td></tr></table>");
        }

        if (footerNote != null && !footerNote.isBlank()) {
            h.append("<p style=\"margin:24px 0 0;font-size:14px;line-height:1.6;color:").append(MUTED).append(";\">").append(esc(footerNote)).append("</p>");
        }
        h.append("</td></tr>");

        // Footer
        h.append("<tr><td style=\"padding:24px 12px 0;text-align:center;font-family:").append(FONT).append(";font-size:12.5px;line-height:1.7;color:").append(MUTED).append(";\">")
         .append("<strong style=\"color:").append(INK).append(";\">Memories Made Visible Photography</strong><br>")
         .append("Winter Haven, FL &middot; Serving all of Polk County<br>")
         .append("<a href=\"").append(esc(site)).append("/\" style=\"color:").append(ACCENT).append(";text-decoration:none;\">").append(esc(site.replaceFirst("^https?://", ""))).append("</a>")
         .append("</td></tr>");

        h.append("</table></td></tr></table></body></html>");

        return new Rendered(h.toString(), plainText(heading, bodyText, details, ctaLabel, ctaUrl, footerNote, site));
    }

    private static String plainText(String heading, String body, List<Detail> details, String ctaLabel, String ctaUrl,
                                    String footerNote, String site) {
        StringBuilder t = new StringBuilder();
        t.append(heading).append("\n\n").append(body == null ? "" : body.strip()).append("\n");
        if (details != null && !details.isEmpty()) {
            t.append("\n");
            for (Detail d : details) {
                if (d.value() == null || d.value().isBlank()) continue;
                t.append(d.label()).append(": ").append(d.value()).append("\n");
            }
        }
        if (ctaLabel != null && !ctaLabel.isBlank() && ctaUrl != null) {
            t.append("\n").append(ctaLabel).append(": ").append(ctaUrl).append("\n");
        }
        if (footerNote != null && !footerNote.isBlank()) {
            t.append("\n").append(footerNote).append("\n");
        }
        t.append("\n--\nMemories Made Visible Photography\nWinter Haven, FL · Serving all of Polk County\n").append(site).append("\n");
        return t.toString();
    }

    /** Blank-line-separated paragraphs → <p>; single newlines → <br>. Everything escaped. */
    static String paragraphs(String body) {
        if (body == null || body.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String para : body.strip().split("\\n\\s*\\n")) {
            out.append("<p style=\"margin:0 0 16px;font-size:16px;line-height:1.65;color:").append(INK).append(";\">")
               .append(esc(para.strip()).replace("\n", "<br>"))
               .append("</p>");
        }
        return out.toString();
    }

    private static String firstLine(String body) {
        if (body == null) return "";
        String s = body.strip();
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
