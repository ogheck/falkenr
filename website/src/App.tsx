import React from "react";

const installSnippet = `dependencies {
  implementation("io.github.ogheck:spring-devtools-ui:0.1.5")
}`;

const PUBLIC_REPO_URL = "https://github.com/ogheck/falkenr";
const HOSTED_APP_URL = "https://app.falkenr.com/app";
const INFO_EMAIL = "info@falkenr.com";

const statusItems = [
  { label: "Local debugging", value: "complete" },
  { label: "Runtime visibility", value: "complete" },
  { label: "Developer power tools", value: "complete" },
  { label: "Team collaboration", value: "alpha (live)" },
  { label: "Staging + persistence", value: "in progress" },
  { label: "Enterprise controls", value: "early" },
  { label: "Cloud platform", value: "planned" },
];

const FEATURES = {
  runtimeVisibility: [
    "Endpoints explorer",
    "Request capture (headers, body, status)",
    "Logs with filtering and stack traces",
    "Full config inspection",
  ],
  powerTools: [
    "Background jobs viewer",
    "DB query tracking",
    "Dependency graph",
    "Feature flag overrides",
    "Webhook simulator",
    "Fake external services",
    "Time control",
  ],
  collaboration: [
    "Shareable session links",
    "Hosted viewer",
    "Request replay artifacts",
    "Multi-user session view",
  ],
};

const CAL_LINK = (import.meta as any).env?.VITE_BOOK_CALL_URL as string | undefined;
const EARLY_ACCESS_ENDPOINT = ((import.meta as any).env?.VITE_EARLY_ACCESS_ENDPOINT
  || ((import.meta as any).env?.PROD ? "/api/early-access" : undefined)) as string | undefined;
const EARLY_ACCESS_EMAIL = "ogheck@gmail.com";

function mailto(subject: string, body?: string, email = EARLY_ACCESS_EMAIL) {
  const encoded = encodeURIComponent(subject);
  const encodedBody = body ? `&body=${encodeURIComponent(body)}` : "";
  return `mailto:${email}?subject=${encoded}${encodedBody}`;
}

function usePathname() {
  const [pathname, setPathname] = React.useState(() => window.location.pathname || "/");
  React.useEffect(() => {
    const onPopState = () => setPathname(window.location.pathname || "/");
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);
  return pathname;
}

function NavLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <a
      href={href}
      onClick={(event) => {
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        if (!href.startsWith("/")) return;
        event.preventDefault();
        window.history.pushState({}, "", href);
        window.dispatchEvent(new PopStateEvent("popstate"));
      }}
      className="text-xs uppercase tracking-[0.22em] text-mist/70 transition hover:text-white"
    >
      {children}
    </a>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-paper font-body text-ink">
      <div className="fixed inset-0 -z-10 bg-grain bg-[size:10px_10px] opacity-25" />
      <main className="mx-auto flex w-full max-w-[1320px] flex-col px-5 pb-20 pt-6 sm:px-8 lg:px-12">
        <div className="mb-8 flex flex-col gap-4 border-b border-ink/10 pb-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <img
              src="/logo.png"
              alt="Falkenr"
              className="h-9 w-9 rounded-[6px] border border-ink/15 bg-white object-cover"
              loading="eager"
            />
            <div>
              <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Falkenr</div>
              <div className="text-sm text-slate/80">Debug locally. Share instantly.</div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-4">
            <NavLink href="/">Home</NavLink>
            <NavLink href="/quickstart">Quickstart</NavLink>
            <NavLink href="/early-access">Early access</NavLink>
            <NavLink href="/pricing">Pricing</NavLink>
            <a
              href="/app"
              className="inline-flex items-center justify-center border border-ink bg-ink px-4 py-2 text-xs uppercase tracking-[0.22em] text-paper transition hover:bg-pine"
            >
              View demo
            </a>
          </div>
        </div>
        {children}
        <footer className="mt-16 border-t border-ink/10 pt-8 text-sm text-slate/80">
          Falkenr: the runtime, shared.
        </footer>
      </main>
    </div>
  );
}

function HomePage() {
  return (
    <>
      <header className="grid gap-8 pb-10 lg:grid-cols-[1.35fr_0.65fr] lg:items-start">
        <div className="space-y-6">
          <div className="inline-flex items-center gap-3 border border-ink/15 bg-white/50 px-3 py-2 text-[11px] uppercase tracking-[0.28em] text-slate">
            Local-first runtime debugging
            <span className="h-2 w-2 rounded-full bg-ember" />
            share when it matters
          </div>
          <div className="space-y-4">
            <p className="font-display text-[clamp(3rem,8.6vw,6.6rem)] leading-[0.92] tracking-[-0.05em] text-ink">
              Debug your Spring Boot app locally — and let your team see exactly what you see.
            </p>
            <p className="max-w-2xl text-base leading-7 text-slate sm:text-lg">
              Falkenr gives you a zero-config runtime dashboard at
              <span className="mx-2 inline-block border-b border-ink/20 font-mono text-sm text-ink">/_dev</span>
              then lets you securely share that exact state with others. No staging, no repro steps, no guesswork.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <a
              href={PUBLIC_REPO_URL}
              className="inline-flex items-center justify-center border border-ink bg-ink px-5 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:bg-pine"
            >
              Get started (free)
            </a>
            <a
              href="/quickstart"
              className="inline-flex items-center justify-center border border-ink/20 px-5 py-3 text-sm uppercase tracking-[0.22em] text-ink transition hover:border-ink"
            >
              Quickstart
            </a>
            <a
              href="/app"
              className="inline-flex items-center justify-center border border-ink/20 px-5 py-3 text-sm uppercase tracking-[0.22em] text-ink transition hover:border-ink"
            >
              View demo
            </a>
          </div>
        </div>

        <section className="w-full border border-ink/15 bg-white/65 p-5 shadow-plate backdrop-blur">
        <div className="mb-4 text-[11px] uppercase tracking-[0.28em] text-slate">
          Quick install
        </div>

        <pre className="overflow-auto bg-ink px-4 py-4 text-sm leading-6 text-paper">
          <code>{installSnippet}</code>
        </pre>

        <div className="mt-4 space-y-2 text-sm text-slate">
          <div>
            Restart your app and open{" "}
            <span className="font-mono text-ink">/_dev</span>
          </div>
          <div>Java 17+ and Spring Boot 3.2.x / 3.3.x</div>
        </div>
      </section>
      </header>

      <section className="border-y border-ink/10 py-12 lg:py-16">
        <div className="grid gap-8 lg:grid-cols-[0.85fr_1.15fr] lg:items-start">
          <div className="space-y-4">
            <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Product preview</div>
            <h2 className="font-display text-4xl tracking-[-0.04em] text-ink sm:text-5xl">
              The actual runtime dashboard, not a mockup.
            </h2>
            <p className="text-sm leading-7 text-slate sm:text-base">
              Falkenr runs inside your Spring Boot app and exposes live runtime state through a local dashboard first.
              Hosted sharing builds on the same session when another developer needs to inspect it with you.
            </p>
          </div>
          <figure className="border border-ink/15 bg-white/70 p-3 shadow-plate">
            <img
              src="/demo/falkenr-demo.gif"
              alt="Short Falkenr demo showing install, local dashboard inspection, session sharing, and collaboration"
              className="aspect-[3/2] w-full object-cover"
              loading="lazy"
            />
            <figcaption className="mt-3 text-xs uppercase tracking-[0.22em] text-slate/80">
              Short workflow demo
            </figcaption>
          </figure>
        </div>
      </section>

      <section className="grid gap-8 border-t border-ink/10 py-12 lg:grid-cols-2 lg:py-16">
        <div className="space-y-4">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">How it works</div>
          <h2 className="font-display text-4xl tracking-[-0.04em] text-ink sm:text-5xl">
            Local runtime first, collaboration when you need it.
          </h2>
        </div>
        <div className="grid gap-4">
          {[
            {
              title: "1. Start local",
              body: "Add one dependency. Restart your app. Open /_dev. See endpoints, requests, logs, config, jobs, and more instantly.",
            },
            {
              title: "2. Share instantly",
              body: "Open a secure tunnel and generate a link. No firewall changes. No deployment.",
            },
            {
              title: "3. Collaborate",
              body: "Teammates open the link and see exactly what you see: requests, logs, runtime state, live.",
            },
            {
              title: "4. Scale (coming)",
              body: "Persist sessions, compare environments, and manage teams centrally.",
            },
          ].map((step) => (
            <div key={step.title} className="border border-ink/15 bg-white/60 p-5">
              <div className="text-[11px] uppercase tracking-[0.28em] text-ember">{step.title}</div>
              <p className="mt-4 text-sm leading-6 text-slate">{step.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="border-y border-ink/10 py-12 lg:py-16">
        <div className="grid gap-8 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-6">
            <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Core value</div>
            <h2 className="font-display text-4xl tracking-[-0.04em] text-ink sm:text-5xl">Stop debugging alone.</h2>
            <p className="text-sm leading-7 text-slate sm:text-base">
              Most debugging breaks down when it leaves your machine:
            </p>
            <div className="grid gap-2 text-sm text-slate sm:grid-cols-2">
              {[
                "Can you reproduce this?",
                "Send me the logs",
                "Works on my machine",
                "Try hitting this endpoint",
              ].map((line) => (
                <div key={line} className="border-l border-ink/20 pl-3">{line}</div>
              ))}
            </div>
            <p className="text-sm leading-7 text-slate sm:text-base">
              Falkenr replaces all of that with a single link.
            </p>
          </div>

          <div className="border border-ink/15 bg-fog/90 p-5">
            <div className="mb-4 text-[11px] uppercase tracking-[0.28em] text-slate">Why Falkenr is different</div>
            <div className="space-y-4 text-sm leading-6 text-slate">
              <div>
                <div className="font-medium text-ink">Zero setup</div>
                <div>No database. No services. No config. Just add the dependency.</div>
              </div>
              <div>
                <div className="font-medium text-ink">Local-first</div>
                <div>Everything works entirely on your machine.</div>
              </div>
              <div>
                <div className="font-medium text-ink">Share when needed</div>
                <div>Turn on remote access only when you want to collaborate.</div>
              </div>
              <div>
                <div className="font-medium text-ink">Not another observability tool</div>
                <div>This is your actual runtime, not logs after the fact.</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-8 py-12 lg:grid-cols-3 lg:py-16">
        <div className="lg:col-span-1">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Features</div>
          <h2 className="mt-4 font-display text-4xl tracking-[-0.04em] text-ink sm:text-5xl">
            Everything you need, built into your app.
          </h2>
          <p className="mt-4 text-sm leading-6 text-slate">
            Real runtime surfaces, plus the power tools you reach for when a session turns messy.
          </p>
        </div>

        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-ember">Runtime visibility</div>
          <ul className="mt-4 space-y-2 text-sm text-slate">
            {FEATURES.runtimeVisibility.map((item) => <li key={item}>• {item}</li>)}
          </ul>
        </div>

        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-ember">Developer power tools</div>
          <ul className="mt-4 space-y-2 text-sm text-slate">
            {FEATURES.powerTools.map((item) => <li key={item}>• {item}</li>)}
          </ul>
        </div>

        <div className="border border-ink/15 bg-white/60 p-5 lg:col-start-2 lg:col-span-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-ember">Collaboration (alpha)</div>
          <ul className="mt-4 grid gap-2 text-sm text-slate sm:grid-cols-2">
            {FEATURES.collaboration.map((item) => <li key={item}>• {item}</li>)}
          </ul>
        </div>
      </section>

      <section className="border-y border-ink/10 py-12 lg:py-16">
        <div className="grid gap-8 lg:grid-cols-[1fr_1fr]">
          <div>
            <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Current product status</div>
            <h2 className="mt-4 font-display text-4xl tracking-[-0.04em] text-ink sm:text-5xl">
              Built and running.
            </h2>
            <p className="mt-4 text-sm leading-6 text-slate">
              Live relay deployed (alpha), secure HTTPS via Cloudflare tunnel, cross-machine debugging working today,
              hosted viewer and session sharing implemented.
            </p>
          </div>
          <div className="border border-ink/15 bg-fog/90 p-5">
            <div className="mb-4 text-[11px] uppercase tracking-[0.28em] text-slate">Status</div>
            <div className="grid gap-3 text-sm text-slate sm:grid-cols-2">
              {statusItems.map((item) => (
                <div key={item.label} className="border-t border-ink/15 pt-3">
                  <div className="text-[11px] uppercase tracking-[0.22em] text-slate/80">{item.label}</div>
                  <div className="mt-1 font-medium text-ink">{item.value}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-8 py-12 lg:grid-cols-[0.9fr_1.1fr] lg:py-16">
        <div className="space-y-4">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Where it fits</div>
          <h2 className="font-display text-4xl tracking-[-0.04em] text-ink sm:text-5xl">
            From solo debugging to shared sessions.
          </h2>
        </div>
        <div className="grid gap-4">
          {[
            { label: "During development", note: "Understand your app instantly without adding tooling." },
            { label: "During debugging", note: "Capture the exact request and state that caused the issue." },
            { label: "Across teammates", note: "Share a live debugging session instead of writing repro steps." },
            { label: "Across time zones", note: "Leave behind a session someone else can inspect later." },
          ].map((item) => (
            <div key={item.label} className="border border-ink/15 bg-white/60 p-5">
              <div className="text-lg font-medium tracking-[-0.02em] text-ink">{item.label}</div>
              <div className="mt-1 text-sm leading-6 text-slate">{item.note}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="overflow-hidden border border-ink bg-ink text-paper">
        <div className="grid gap-8 px-6 py-10 lg:grid-cols-[1fr_auto] lg:px-8">
          <div className="space-y-3">
            <div className="text-[11px] uppercase tracking-[0.28em] text-sand">Simple pricing</div>
            <h2 className="font-display text-3xl tracking-[-0.04em] text-paper sm:text-4xl">
              Start debugging and sharing in minutes.
            </h2>
            <p className="max-w-2xl text-sm leading-6 text-sand">
              Free: local dashboard for individual developers. Team beta: hosted collaboration and shared debugging
              sessions. Pro and Enterprise remain scoped for larger rollout needs.
            </p>
          </div>
          <div className="flex flex-col gap-3 self-end">
            <a
              href={PUBLIC_REPO_URL}
              className="inline-flex items-center justify-center border border-paper/20 px-6 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:border-paper"
            >
              Get started free
            </a>
            <a
              href="/pricing"
              className="inline-flex items-center justify-center border border-paper/20 px-6 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:border-paper"
            >
              View pricing
            </a>
          </div>
        </div>
      </section>
    </>
  );
}

function QuickstartPage() {
  return (
    <section className="space-y-10">
      <div className="space-y-5">
        <div className="inline-flex items-center gap-3 border border-ink/15 bg-white/50 px-3 py-2 text-[11px] uppercase tracking-[0.28em] text-slate">
          Quickstart
          <span className="h-2 w-2 rounded-full bg-ember" />
          Spring Boot
        </div>
        <h1 className="font-display text-[clamp(2.6rem,7vw,5rem)] leading-[0.95] tracking-[-0.05em] text-ink">
          Open your first local runtime dashboard in minutes.
        </h1>
        <p className="max-w-2xl text-base leading-7 text-slate sm:text-lg">
          Add the starter, restart your app, and open <span className="font-mono text-ink">/_dev</span>. Start with
          local debugging before turning on hosted collaboration.
        </p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[0.8fr_1.2fr]">
        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Prerequisites</div>
          <div className="mt-4 grid gap-2 text-sm leading-6 text-slate">
            <div>Java 17 or 21</div>
            <div>Spring Boot 3.2.x or 3.3.x</div>
            <div>Spring MVC application</div>
            <div>Gradle or Maven</div>
          </div>
        </div>

        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Gradle</div>
          <pre className="mt-4 overflow-auto bg-ink px-4 py-4 text-sm leading-6 text-paper">
            <code>{installSnippet}</code>
          </pre>
        </div>
      </div>

      <div className="grid gap-4">
        {[
          {
            title: "1. Add the dependency",
            body: "Install the starter in the Spring Boot app you already use for local development.",
          },
          {
            title: "2. Restart locally",
            body: "Run your app normally. Falkenr is enabled for local development and disabled automatically for the prod profile.",
          },
          {
            title: "3. Open /_dev",
            body: "Go to http://localhost:8080/_dev and inspect endpoints, requests, config, logs, jobs, DB queries, feature flags, and dependency state.",
          },
          {
            title: "4. Share only when needed",
            body: "For team debugging, request early access and connect the local dashboard to the hosted relay.",
          },
        ].map((step) => (
          <div key={step.title} className="border border-ink/15 bg-white/60 p-5">
            <div className="text-[11px] uppercase tracking-[0.28em] text-ember">{step.title}</div>
            <p className="mt-3 text-sm leading-6 text-slate">{step.body}</p>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-3">
        <a
          href={PUBLIC_REPO_URL}
          className="inline-flex items-center justify-center border border-ink bg-ink px-5 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:bg-pine"
        >
          Open GitHub
        </a>
        <a
          href="/early-access"
          className="inline-flex items-center justify-center border border-ink/20 px-5 py-3 text-sm uppercase tracking-[0.22em] text-ink transition hover:border-ink"
        >
          Request hosted access
        </a>
      </div>
    </section>
  );
}

function EarlyAccessPage() {
  const [leadForm, setLeadForm] = React.useState({
    name: "",
    email: "",
    company: "",
    teamSize: "",
    pain: "",
  });
  const [leadStatus, setLeadStatus] = React.useState<"idle" | "sending" | "sent" | "error">("idle");

  function updateLeadField(field: keyof typeof leadForm, value: string) {
    setLeadForm((current) => ({ ...current, [field]: value }));
  }

  async function submitLead(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!EARLY_ACCESS_ENDPOINT) {
      window.location.href = mailto(
        "Falkenr early access request",
        [
          `Name: ${leadForm.name}`,
          `Email: ${leadForm.email}`,
          `Company: ${leadForm.company}`,
          `Team size: ${leadForm.teamSize}`,
          `Debugging pain: ${leadForm.pain}`,
        ].join("\n"),
        INFO_EMAIL
      );
      return;
    }

    setLeadStatus("sending");
    try {
      const response = await fetch(EARLY_ACCESS_ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...leadForm,
          source: "website-early-access",
          capturedAt: new Date().toISOString(),
        }),
      });

      if (!response.ok) {
        throw new Error(`Lead capture failed with ${response.status}`);
      }

      setLeadStatus("sent");
      setLeadForm({ name: "", email: "", company: "", teamSize: "", pain: "" });
    } catch {
      setLeadStatus("error");
    }
  }

  return (
    <section className="space-y-10">
      <div className="space-y-5">
        <div className="inline-flex items-center gap-3 border border-ink/15 bg-white/50 px-3 py-2 text-[11px] uppercase tracking-[0.28em] text-slate">
          Early access
          <span className="h-2 w-2 rounded-full bg-ember" />
          early access users
        </div>
        <h1 className="font-display text-[clamp(2.6rem,7vw,5rem)] leading-[0.95] tracking-[-0.05em] text-ink">
          Stop sending repro steps. Share your debugging session instead.
        </h1>
        <p className="max-w-2xl text-base leading-7 text-slate sm:text-lg">
          Falkenr lets you share a live Spring Boot runtime with your team, requests, logs, and state, through a single link.
          We’re looking for 5–10 engineering teams to test this in real workflows.
        </p>
        <div className="flex flex-wrap gap-3">
          <a
            href="mailto:info@falkenr.com?subject=Falkenr%20early%20access%20request&body=Hi%20-%20I'm%20interested%20in%20early%20access%20to%20Falkenr."
            className="inline-flex items-center justify-center border border-ink bg-ink px-5 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:bg-pine"
          >
            Request early access
          </a>

          <a
            href="mailto:info@falkenr.com?subject=Falkenr%2015-min%20call&body=Hi%20-%20I'd%20like%20to%20schedule%20a%2015-minute%20call%20about%20Falkenr."
            className="inline-flex items-center justify-center border border-ink/20 px-5 py-3 text-sm uppercase tracking-[0.22em] text-ink transition hover:border-ink"
          >
            Book a 15-min call
          </a>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">If debugging looks like this</div>
          <div className="mt-4 grid gap-2 text-sm text-slate">
            {[
              "Can you reproduce this?",
              "What request did you send?",
              "Send me the logs",
              "It works on my machine",
            ].map((line) => (
              <div key={line} className="border-l border-ink/20 pl-3">{line}</div>
            ))}
          </div>
          <p className="mt-4 text-sm leading-6 text-slate">
            Debugging breaks the moment it leaves one developer’s environment.
          </p>
        </div>

        <div className="border border-ink/15 bg-fog/90 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">What if you could just send a link?</div>
          <p className="mt-4 text-sm leading-6 text-slate">
            Your teammate opens the link and immediately sees the exact request that failed, logs at that moment,
            runtime config, and the actual system state. No staging deploy. No reproduction steps.
          </p>
          <div className="mt-5 border border-ink/15 bg-white/60 p-4 text-sm text-slate">
            1. Hit an issue locally
            <br />
            2. Open <span className="font-mono text-ink">/_dev</span>
            <br />
            3. Click <span className="font-medium text-ink">Share session</span>
            <br />
            4. Send the link
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Who this is for</div>
          <div className="mt-4 grid gap-2 text-sm text-slate">
            <div>✔ Teams of 3–20 engineers</div>
            <div>✔ Backend-heavy workflows (Spring Boot)</div>
            <div>✔ Frequent debugging across people or environments</div>
            <div>✔ Async or distributed teams</div>
          </div>
          <div className="mt-6 text-[11px] uppercase tracking-[0.28em] text-slate">Not a fit if</div>
          <div className="mt-3 grid gap-2 text-sm text-slate">
            <div>✘ You only debug solo</div>
            <div>✘ You already solved this with internal tooling</div>
            <div>✘ You cannot allow request metadata to leave your network</div>
          </div>
        </div>

        <div className="border border-ink/15 bg-white/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Current status</div>
          <div className="mt-4 grid gap-2 text-sm text-slate">
            <div>• Local debugging: complete</div>
            <div>• Runtime visibility: complete</div>
            <div>• Team sharing: working (alpha)</div>
            <div>• Hosted viewer: live</div>
            <div>• Persistence + staging: in progress</div>
          </div>
          <p className="mt-4 text-sm leading-6 text-slate">
            This is not a polished SaaS yet. This is a working system we want real teams to break.
          </p>
        </div>
      </div>

      <div className="border border-ink bg-ink p-6 text-paper">
        <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-end">
          <div>
            <div className="text-[11px] uppercase tracking-[0.28em] text-sand">Want to try this with your team?</div>
            <div className="mt-3 text-2xl font-medium tracking-[-0.03em]">
              We’re onboarding a small number of teams manually.
            </div>
          </div>
          <div className="flex flex-col gap-3">
            <a
              href={mailto(
                "Falkenr early access request",
                "Hi - I'm interested in early access to Falkenr.",
                INFO_EMAIL
              )}
              className="inline-flex items-center justify-center border border-paper/20 px-6 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:border-paper"
            >
              Request early access
            </a>
            <a
              href={CAL_LINK || mailto(
                "Falkenr 15-min call",
                "Hi - I'd like to schedule a 15-minute call about Falkenr.",
                INFO_EMAIL
              )}
              className="inline-flex items-center justify-center border border-paper/20 px-6 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:border-paper"
            >
              Book a 15-min call
            </a>
          </div>
        </div>
      </div>

      <form onSubmit={submitLead} className="grid gap-4 border border-ink/15 bg-white/70 p-5 lg:grid-cols-2">
        <div className="lg:col-span-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">Join early access</div>
          <p className="mt-2 text-sm leading-6 text-slate">
            Tell us who should evaluate Falkenr and what debugging workflow you want to test first.
          </p>
        </div>
        <label className="grid gap-2 text-sm text-slate">
          Name
          <input
            required
            value={leadForm.name}
            onChange={(event) => updateLeadField("name", event.target.value)}
            className="border border-ink/20 bg-paper px-4 py-3 text-ink outline-none focus:border-ink"
          />
        </label>
        <label className="grid gap-2 text-sm text-slate">
          Work email
          <input
            required
            type="email"
            value={leadForm.email}
            onChange={(event) => updateLeadField("email", event.target.value)}
            className="border border-ink/20 bg-paper px-4 py-3 text-ink outline-none focus:border-ink"
          />
        </label>
        <label className="grid gap-2 text-sm text-slate">
          Company
          <input
            value={leadForm.company}
            onChange={(event) => updateLeadField("company", event.target.value)}
            className="border border-ink/20 bg-paper px-4 py-3 text-ink outline-none focus:border-ink"
          />
        </label>
        <label className="grid gap-2 text-sm text-slate">
          Team size
          <input
            value={leadForm.teamSize}
            onChange={(event) => updateLeadField("teamSize", event.target.value)}
            className="border border-ink/20 bg-paper px-4 py-3 text-ink outline-none focus:border-ink"
          />
        </label>
        <label className="grid gap-2 text-sm text-slate lg:col-span-2">
          Debugging workflow to test
          <textarea
            rows={4}
            value={leadForm.pain}
            onChange={(event) => updateLeadField("pain", event.target.value)}
            className="resize-y border border-ink/20 bg-paper px-4 py-3 text-ink outline-none focus:border-ink"
          />
        </label>
        <div className="flex flex-wrap items-center gap-3 lg:col-span-2">
          <button
            type="submit"
            disabled={leadStatus === "sending"}
            className="border border-ink bg-ink px-5 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:bg-pine disabled:cursor-not-allowed disabled:opacity-60"
          >
            {leadStatus === "sending" ? "Sending" : "Request access"}
          </button>
          {leadStatus === "sent" ? <span className="text-sm text-pine">Request received.</span> : null}
          {leadStatus === "error" ? (
            <span className="text-sm text-ember">Could not submit. Email fallback is still available.</span>
          ) : null}
        </div>
      </form>
    </section>
  );
}

function PricingPage() {
  return (
    <section className="space-y-10">
      <div className="space-y-5">
        <div className="inline-flex items-center gap-3 border border-ink/15 bg-white/50 px-3 py-2 text-[11px] uppercase tracking-[0.28em] text-slate">
          Pricing
          <span className="h-2 w-2 rounded-full bg-ember" />
          paid beta
        </div>
        <h1 className="font-display text-[clamp(2.6rem,7vw,5rem)] leading-[0.95] tracking-[-0.05em] text-ink">
          Pricing for real debugging, not dashboards.
        </h1>
        <p className="max-w-2xl text-base leading-7 text-slate sm:text-lg">
          Start free locally. Upgrade to Team when your team needs hosted collaboration and shared debugging sessions.
        </p>
      </div>

      <div className="grid gap-5 lg:grid-cols-4">
        <PricingCard
          name="Free"
          price="$0"
          tag="For individual developers"
          bullets={[
            "Local dashboard at /_dev",
            "Endpoints, requests, logs, config",
            "Jobs, DB queries, feature overrides",
            "Runs entirely in your app",
            "No database required",
          ]}
          cta={{ label: "Get started", href: PUBLIC_REPO_URL }}
        />
        <PricingCard
          name="Team"
          price="$20 / month"
          tag="For teams debugging across machines"
          bullets={[
            "Shareable debugging sessions",
            "Remote access via secure tunnel",
            "Hosted viewer",
            "Shared request replay",
            "Multi-developer session view",
            "Stripe Checkout and Team entitlement",
          ]}
          cta={{ label: "Start Team beta", href: HOSTED_APP_URL }}
          highlight
        />
        <PricingCard
          name="Pro"
          price="$79 / app / month"
          tag="For staging and QA workflows"
          bullets={[
            "Everything in Team",
            "Persistent request history",
            "Environment comparison",
            "Config diff",
            "API testing tools",
            "Mock responses",
          ]}
          cta={{ label: "Join waitlist", href: mailto("Falkenr Pro waitlist") }}
        />
        <PricingCard
          name="Enterprise"
          price="Custom"
          tag="For large teams and production-safe usage"
          bullets={[
            "SSO",
            "RBAC and access control",
            "Audit logs",
            "Data masking policies",
            "Production-safe sampling",
            "Governance controls",
          ]}
          cta={{ label: "Contact us", href: mailto("Falkenr enterprise inquiry") }}
        />
      </div>

      <div className="grid gap-6 border border-ink/15 bg-fog/90 p-6 lg:grid-cols-2">
        <div>
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">When do you actually pay?</div>
          <p className="mt-4 text-sm leading-7 text-slate">
            You don’t pay for Falkenr until debugging stops being local. You upgrade when you need to show a teammate what
            happened, debugging across environments is slowing you down, or logs and screenshots aren’t enough anymore.
            If you never hit that point, Falkenr stays free.
          </p>
        </div>
        <div>
          <div className="text-[11px] uppercase tracking-[0.28em] text-slate">No lock-in</div>
          <div className="mt-4 grid gap-2 text-sm text-slate">
            <div>• Free local usage forever</div>
            <div>• No required cloud setup</div>
            <div>• Collaboration is opt-in</div>
            <div>• You control when data leaves your machine</div>
          </div>
          <div className="mt-6 flex flex-wrap gap-3">
            <a
              href={PUBLIC_REPO_URL}
              className="inline-flex items-center justify-center border border-ink bg-ink px-5 py-3 text-sm uppercase tracking-[0.22em] text-paper transition hover:bg-pine"
            >
              Get started
            </a>
            <a
              href={HOSTED_APP_URL}
              className="inline-flex items-center justify-center border border-ink/20 px-5 py-3 text-sm uppercase tracking-[0.22em] text-ink transition hover:border-ink"
            >
              Start Team beta
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}

function PricingCard({
  name,
  price,
  tag,
  bullets,
  cta,
  highlight,
}: {
  name: string;
  price: string;
  tag: string;
  bullets: string[];
  cta: { label: string; href: string };
  highlight?: boolean;
}) {
  return (
    <div className={`border p-5 ${highlight ? "border-ink bg-white" : "border-ink/15 bg-white/60"}`}>
      <div className="text-[11px] uppercase tracking-[0.28em] text-ember">{name}</div>
      <div className="mt-3 text-2xl font-medium tracking-[-0.03em] text-ink">{price}</div>
      <div className="mt-2 text-sm leading-6 text-slate">{tag}</div>
      <ul className="mt-4 space-y-2 text-sm text-slate">
        {bullets.map((item) => <li key={item}>• {item}</li>)}
      </ul>
      <a
        href={cta.href}
        className={`mt-6 inline-flex w-full items-center justify-center border px-4 py-3 text-sm uppercase tracking-[0.22em] transition ${
          highlight ? "border-ink bg-ink text-paper hover:bg-pine" : "border-ink/20 text-ink hover:border-ink"
        }`}
      >
        {cta.label}
      </a>
    </div>
  );
}

export default function App() {
  const pathname = usePathname();
  const page = pathname.replace(/\/+$/, "") || "/";

  let content: React.ReactNode = <HomePage />;
  if (page === "/quickstart") content = <QuickstartPage />;
  if (page === "/early-access") content = <EarlyAccessPage />;
  if (page === "/pricing") content = <PricingPage />;

  return <Shell>{content}</Shell>;
}
