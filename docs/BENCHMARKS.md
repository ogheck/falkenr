# Benchmarks

This document records lightweight release-gate measurements for the local starter. The purpose is not to produce perfect lab-grade numbers. The goal is to keep startup and idle-memory overhead explicit and bounded as the MVP grows.

## Methodology

Two example applications are compared:

- `examples/baseline-app`: Spring Boot web app without `spring-devtools-ui`
- `examples/demo-app`: same general app shape with `spring-devtools-ui` on the classpath

The benchmark script:

1. builds both example boot jars
2. launches each app in a fresh JVM
3. waits for the `"Started ... in"` log line
4. samples resident memory after a short idle window
5. reports baseline, devtools-ui, and delta values

Run it with:

```bash
./scripts/benchmark-starter.sh
```

## Current Release Snapshot

Current release baseline was measured with the included benchmark script on the local development machine used for release preparation.

- startup overhead target: under 2 seconds relative to the baseline app
- idle RSS overhead target: under 80 MB relative to the baseline app
- latest sampled startup delta: `-0.016s` (`1.015s` baseline vs `0.999s` with starter)
- latest sampled idle RSS delta: `3.16 MB` (`156.66 MB` baseline vs `159.81 MB` with starter)

The script prints the actual numbers each time it runs. Re-run it after collector changes, UI bundling changes, or release infrastructure changes that may affect startup.

## Interpretation

- Startup time is the value reported by Spring Boot in the application start log.
- Idle memory is a practical RSS sample after the app has finished booting and remained idle briefly.
- Numbers will vary by machine, JDK, and OS. Track direction and regression rather than treating one run as absolute truth.
