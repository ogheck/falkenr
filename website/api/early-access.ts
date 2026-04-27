type LeadPayload = {
  name?: string;
  email?: string;
  company?: string;
  teamSize?: string;
  pain?: string;
  source?: string;
  capturedAt?: string;
};

const MAX_FIELD_LENGTH = 2000;

function clean(value: unknown) {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, MAX_FIELD_LENGTH);
}

function isEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export default async function handler(req: any, res: any) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const payload = (req.body || {}) as LeadPayload;
  const lead = {
    name: clean(payload.name),
    email: clean(payload.email),
    company: clean(payload.company),
    teamSize: clean(payload.teamSize),
    pain: clean(payload.pain),
    source: clean(payload.source) || "website-early-access",
    capturedAt: clean(payload.capturedAt) || new Date().toISOString(),
  };

  if (!lead.name || !isEmail(lead.email)) {
    return res.status(400).json({ error: "invalid_lead" });
  }

  const webhookUrl = process.env.LEAD_CAPTURE_WEBHOOK_URL;
  if (!webhookUrl) {
    return res.status(500).json({ error: "lead_capture_not_configured" });
  }

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (process.env.LEAD_CAPTURE_WEBHOOK_TOKEN) {
    headers.Authorization = `Bearer ${process.env.LEAD_CAPTURE_WEBHOOK_TOKEN}`;
  }

  const response = await fetch(webhookUrl, {
    method: "POST",
    headers,
    body: JSON.stringify(lead),
  });

  if (!response.ok) {
    return res.status(502).json({ error: "lead_capture_failed" });
  }

  return res.status(202).json({ ok: true });
}
