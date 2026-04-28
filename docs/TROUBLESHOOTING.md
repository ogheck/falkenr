# Falkenr Troubleshooting

This guide covers the most common setup, billing, and hosted collaboration issues during the Falkenr paid beta.

## Checkout Fails Or Does Not Open

Symptoms:

- The Team upgrade button does not redirect to Stripe.
- `POST /sessions/billing/checkout` returns an error.
- The browser stays on the hosted dashboard after clicking upgrade.

Checks:

1. Confirm you are signed into the hosted dashboard at `https://app.falkenr.com/app`.
2. Refresh the page and try the upgrade button again.
3. If you are testing with `curl`, do not use `GET`. The checkout route only accepts `POST`.
4. Confirm the organization owner is using the account session that owns the hosted organization.

Operator checks:

- Confirm the relay has a live Stripe secret key configured.
- Confirm the Team price ID matches the live Stripe price.
- Confirm the checkout success URL is `https://app.falkenr.com/app?billing=success`.
- Confirm the checkout cancel URL is `https://app.falkenr.com/pricing?billing=cancelled`.
- Check relay logs for Stripe API errors and missing account-session errors.

## Checkout Completes But Team Is Still Locked

Symptoms:

- Stripe checkout succeeds, but hosted Team features still show `plan_required`.
- The dashboard still shows the organization as Free.

Checks:

1. Return to `https://app.falkenr.com/app?billing=success`.
2. Refresh the hosted dashboard after a few seconds.
3. Confirm the same owner account is signed in.

Operator checks:

- In Stripe, open the webhook destination and confirm `checkout.session.completed` was delivered successfully.
- Confirm the webhook endpoint is `https://app.falkenr.com/sessions/billing/stripe/webhook`.
- Confirm the relay webhook secret matches the Stripe destination signing secret.
- If the webhook failed, replay the failed delivery from Stripe after fixing the secret or endpoint.

## Missing Account Session

Symptoms:

- Hosted dashboard shows an unsigned or disconnected state.
- API requests fail because `accountSession` is missing.
- Billing routes reject the request even though the user is visible in the browser.

Checks:

1. Open `https://app.falkenr.com/app` directly.
2. Sign in or accept an owner invitation again.
3. Avoid manually calling billing routes unless you include the current owner account session.

Operator checks:

- Confirm the owner account exists in the relay account directory.
- Confirm the organization owner has an active account-session record.
- If a browser session is stale, have the customer sign in again instead of reusing an old link.

## `plan_required`

Symptoms:

- A hosted operation returns HTTP `402`.
- The response includes `plan_required`.
- Invitations, replay preparation, or Team features are unavailable.

Meaning:

The relay recognizes the organization but does not see an active or trialing Team entitlement for that organization.

Checks:

1. Confirm the organization owner completed Team checkout.
2. Confirm the subscription is active or trialing in Stripe.
3. Refresh the hosted dashboard.

Operator checks:

- Confirm subscription events are mapped to the correct relay organization.
- Confirm canceled, expired, or past-due subscriptions are expected to block Team-only actions.
- If the organization should be active, inspect recent Stripe webhook deliveries.

## Relay Attach Fails

Symptoms:

- The local Spring Boot app cannot attach to the hosted relay.
- The hosted dashboard shows no attached session.
- The local dashboard shows relay connection errors.

Checks:

1. Confirm the local app includes the Falkenr starter dependency.
2. Open the local dashboard at `http://localhost:8080/_dev`.
3. Set the relay URL to `https://app.falkenr.com`.
4. Confirm the local machine can reach `https://app.falkenr.com/sessions/status`.
5. Restart the local app if the session was created before relay settings changed.

Operator checks:

- Confirm `/sessions/status` returns `{"status":"ok"}`.
- Check whether the relay is rejecting the attach because the organization is not entitled.
- Check relay logs for invalid token, expired token, or owner mismatch messages.

## Share Link Or Viewer Join Fails

Symptoms:

- A teammate opens an invitation link but cannot join.
- The viewer lands on the hosted dashboard without seeing the session.
- The owner sees the session, but the teammate does not.

Checks:

1. Have the owner create a new invitation link.
2. Make sure the teammate opens the latest link in a normal browser window.
3. Confirm the owner has an attached relay session.
4. Confirm Team entitlement is active.

Operator checks:

- Confirm the invitation token has not expired or already been revoked.
- Confirm the viewer account was created or resolved by the relay.
- Confirm the hosted session still exists in relay state.

## Replay Artifact Missing

Symptoms:

- A teammate can join but cannot inspect the expected replay.
- The owner prepared a replay, but the viewer cannot find it.

Checks:

1. Recreate the request locally.
2. Prepare the replay again from the owner dashboard.
3. Confirm the viewer refreshes the hosted session after the replay is prepared.

Operator checks:

- Confirm replay artifacts are persisted in the relay state file.
- Confirm the replay belongs to the same organization and session as the viewer.
- If the relay was restarted, check whether the state file loaded successfully.

## What To Send Support

Send:

- The organization name.
- The owner email.
- Approximate time of the failure, including timezone.
- The visible error code or message.
- Whether the issue is checkout, entitlement, relay attach, invitation, viewer join, or replay.

Do not send:

- Stripe secret keys.
- Webhook signing secrets.
- Local app secrets.
- Raw request bodies containing customer or production data.
