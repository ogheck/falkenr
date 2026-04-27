# Falkenr Website

Vite + React public site for Falkenr.

## Development

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Lead Capture

The early-access form posts to `VITE_EARLY_ACCESS_ENDPOINT` when set.

For production Vercel builds, the site defaults to:

```text
/api/early-access
```

Configure these Vercel environment variables:

```text
LEAD_CAPTURE_WEBHOOK_URL=https://example.com/webhook
LEAD_CAPTURE_WEBHOOK_TOKEN=optional-shared-secret
```

`LEAD_CAPTURE_WEBHOOK_URL` must accept a JSON `POST` payload:

```json
{
  "name": "Jane Developer",
  "email": "jane@example.com",
  "company": "Example Co",
  "teamSize": "8",
  "pain": "We share repro steps across time zones.",
  "source": "website-early-access",
  "capturedAt": "2026-04-27T12:00:00.000Z"
}
```

If the webhook token is configured, `/api/early-access` forwards it as:

```text
Authorization: Bearer <token>
```
