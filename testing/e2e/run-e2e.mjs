#!/usr/bin/env node
/**
 * Cross-service end-to-end runner for MediFlow.
 *
 * Starts a throwaway stack (PostgreSQL, Redis, Kafka on alternate ports), launches the Spring Boot
 * services from their executable jars, waits until every `/actuator/health` is `UP`, runs the
 * journeys in `journeys.mjs` against the real API gateway, then tears everything down.
 *
 * Everything is generated per run: `JWT_SECRET`, the database password and all mappings. No
 * developer-specific state is read, so the same command works on a clean clone and in CI.
 *
 * Usage:
 *   node testing/e2e/run-e2e.mjs                 # core services (fast)
 *   node testing/e2e/run-e2e.mjs --profile=full  # adds notification + analytics (Kafka)
 *   node testing/e2e/run-e2e.mjs --build         # package the jars first
 *   node testing/e2e/run-e2e.mjs --keep          # leave the stack running for debugging
 *
 * Requirements: Docker with Compose v2, JDK 21, Node 24, Maven (only with --build).
 */
import { spawn, spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { runJourneys } from './journeys.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, '..', '..');
const COMPOSE_FILE = join(HERE, 'docker-compose.e2e.yml');
const LOG_DIR = join(HERE, '.logs');

const DB_HOST = '127.0.0.1';
const DB_PORT = 54321;
const REDIS_PORT = 56379;
const KAFKA_PORT = 59092;

/** Service definitions: shifted port, owned database and the profile that starts it. */
const SERVICES = {
  'auth-service': { port: 18081, database: 'mediflow_auth_db', profile: 'core', artifact: 'auth-service' },
  'patient-service': { port: 18083, database: 'patient_db', profile: 'core', artifact: 'patient-service' },
  'appointment-service': { port: 18084, database: 'appointment_db', profile: 'core', artifact: 'appointment-service' },
  'doctor-service': { port: 18085, database: 'doctor_db', profile: 'core', artifact: 'doctor-service' },
  'billing-service': { port: 18087, database: 'billing_db', profile: 'core', artifact: 'billing-service' },
  'medical-record-service': { port: 18091, database: 'medical_record_db', profile: 'core', artifact: 'medical-record-service' },
  'notification-service': { port: 18089, database: 'notification_db', profile: 'full', artifact: 'notification-service' },
  'analytics-service': { port: 18088, database: 'analytics_db', profile: 'full', artifact: 'analytics-service' },
  'api-gateway': { port: 18080, database: null, profile: 'core', artifact: 'api-gateway' },
};

const DEFAULT_JAVA_OPTS = [
  // CI runners and laptops are memory constrained; these services do not need a large heap.
  '-Xmx256m',
  '-XX:MaxMetaspaceSize=192m',
  '-Xss512k',
  '-XX:+UseSerialGC',
  '-XX:TieredStopAtLevel=1',
  // Scheduling and audit timestamps are interpreted in UTC, matching CLINIC_TIME_ZONE.
  '-Duser.timezone=UTC',
];

function parseArgs(argv) {
  const options = { profile: 'core', keep: false, build: false };
  for (const arg of argv) {
    if (arg === '--build') options.build = true;
    else if (arg === '--keep') options.keep = true;
    else if (arg.startsWith('--profile=')) options.profile = arg.slice('--profile='.length);
    else if (arg === '--help' || arg === '-h') options.help = true;
    else throw new Error(`unknown argument: ${arg}`);
  }
  if (!['core', 'full'].includes(options.profile)) {
    throw new Error(`unknown profile "${options.profile}" (expected core or full)`);
  }
  return options;
}

function printHelp() {
  console.log(`MediFlow cross-service E2E

  node testing/e2e/run-e2e.mjs [options]

  --profile=core|full  core (7 services, default) or full (+ notification, analytics)
  --build              run "mvn -B -ntp -DskipTests package" before starting
  --keep               keep containers and services running afterwards
  -h, --help           show this help

Environment:
  E2E_JAVA_OPTS        override the JVM options for the services
  E2E_STARTUP_TIMEOUT_MS  per-service readiness timeout (default 240000)
`);
}

function selectedServices(profile) {
  return Object.entries(SERVICES).filter(
    ([, service]) => profile === 'full' || service.profile === 'core',
  );
}

function log(message = '') {
  console.log(message);
}

function tail(path, lines = 40) {
  try {
    return readFileSync(path, 'utf8').split(/\r?\n/).slice(-lines).join('\n');
  } catch {
    return '(no log file)';
  }
}

function run(command, args, { env = process.env, cwd = REPO_ROOT, label = command, shell = false } = {}) {
  const result = spawnSync(command, args, { cwd, env, stdio: 'inherit', shell });
  if (result.error) throw new Error(`${label} failed to start: ${result.error.message}`);
  if (result.status !== 0) throw new Error(`${label} exited with code ${result.status}`);
}

function jarFor(service) {
  return join(REPO_ROOT, 'backend', service.artifact, 'target', `${service.artifact}-1.0.0-exec.jar`);
}

/** Compose environment. Values are generated per run and never written to disk. */
function composeEnvironment(secrets) {
  return {
    ...process.env,
    E2E_DB_USERNAME: 'postgres',
    E2E_DB_PASSWORD: secrets.dbPassword,
  };
}

function startInfrastructure(secrets) {
  log('==> Starting E2E infrastructure (PostgreSQL, Redis, Kafka)');
  const env = composeEnvironment(secrets);
  // A previous, possibly interrupted run may have left containers with different credentials.
  spawnSync('docker', ['compose', '-f', COMPOSE_FILE, 'down', '-v', '--remove-orphans'], {
    cwd: REPO_ROOT,
    env,
    stdio: 'ignore',
  });
  run(
    'docker',
    ['compose', '-f', COMPOSE_FILE, 'up', '-d', '--wait', '--wait-timeout', '180'],
    { env, label: 'docker compose up' },
  );
}

function stopInfrastructure(secrets) {
  log('==> Stopping E2E infrastructure');
  spawnSync('docker', ['compose', '-f', COMPOSE_FILE, 'down', '-v', '--remove-orphans'], {
    cwd: REPO_ROOT,
    env: composeEnvironment(secrets),
    stdio: 'inherit',
  });
}

function serviceEnvironment(secrets) {
  const base = {
    ...process.env,
    JWT_SECRET: secrets.jwtSecret,
    DB_USERNAME: 'postgres',
    DB_PASSWORD: secrets.dbPassword,
    REDIS_HOST: DB_HOST,
    REDIS_PORT: String(REDIS_PORT),
    KAFKA_BOOTSTRAP_SERVERS: `${DB_HOST}:${KAFKA_PORT}`,
    CLINIC_TIME_ZONE: 'UTC',
    TZ: 'UTC',
    HEALTH_SHOW_DETAILS: 'never',
    // The browser suite owns the frontend origin; the gateway is the only CORS owner.
    CORS_ALLOWED_ORIGINS: 'http://127.0.0.1:3100',
    // Direct service-to-service URLs on the shifted ports.
    AUTH_SERVICE_URL: `http://127.0.0.1:${SERVICES['auth-service'].port}`,
    DOCTOR_SERVICE_URL: `http://127.0.0.1:${SERVICES['doctor-service'].port}`,
    GATEWAY_SELF_URL: `http://127.0.0.1:${SERVICES['api-gateway'].port}`,
    AUTH_SERVICE: `http://127.0.0.1:${SERVICES['auth-service'].port}`,
  };
  for (const [name, service] of Object.entries(SERVICES)) {
    if (name === 'api-gateway') continue;
    base[`${name.replace(/-/g, '_').toUpperCase()}_URL`] = `http://127.0.0.1:${service.port}`;
  }
  return base;
}

function startService(name, service, secrets) {
  const jar = jarFor(service);
  if (!existsSync(jar)) {
    throw new Error(`missing jar ${jar} — run "node testing/e2e/run-e2e.mjs --build" first`);
  }
  const logPath = join(LOG_DIR, `${name}.log`);
  const javaOptions = (process.env.E2E_JAVA_OPTS ? process.env.E2E_JAVA_OPTS.split(/\s+/) : DEFAULT_JAVA_OPTS);
  const java = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const args = [
    ...javaOptions,
    '-jar',
    jar,
    `--server.port=${service.port}`,
    '--spring.profiles.active=seed',
    '--management.endpoint.health.probes.enabled=true',
  ];

  // Log straight to a file descriptor so the services survive this process exiting (--keep).
  const logFd = openSync(logPath, 'w');
  const child = spawn(java, args, {
    cwd: REPO_ROOT,
    env: { ...serviceEnvironment(secrets), DB_URL: service.database ? jdbcUrl(service.database) : undefined },
    detached: process.platform !== 'win32',
    windowsHide: true,
    stdio: ['ignore', logFd, logFd],
  });
  closeSync(logFd);
  const state = { name, service, child, logPath, exited: false, exitCode: null };
  child.on('exit', (code, signal) => {
    state.exited = true;
    state.exitCode = code ?? signal;
  });
  child.unref();
  log(`    started ${name.padEnd(23)} http://127.0.0.1:${service.port}  (log: testing/e2e/.logs/${name}.log)`);
  return state;
}

function jdbcUrl(database) {
  return `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${database}`;
}

async function isHealthy(url) {
  try {
    const response = await fetch(new URL('/actuator/health', url), {
      signal: AbortSignal.timeout(3_000),
      headers: { accept: 'application/json' },
    });
    if (response.status !== 200) return false;
    const body = await response.json();
    return body?.status === 'UP';
  } catch {
    return false;
  }
}

async function waitForServices(states) {
  const timeoutMs = Number(process.env.E2E_STARTUP_TIMEOUT_MS ?? 240_000);
  const deadline = Date.now() + timeoutMs;
  const pending = new Map(states.map((state) => [state.name, state]));

  while (pending.size > 0) {
    for (const [name, state] of [...pending]) {
      if (state.exited) {
        throw new Error(
          `${name} exited during startup (code ${state.exitCode}).\n--- ${state.logPath} ---\n${tail(state.logPath)}`,
        );
      }
      if (await isHealthy(`http://127.0.0.1:${state.service.port}`)) {
        pending.delete(name);
        log(`    ready   ${name}`);
      }
    }
    if (pending.size === 0) return;
    if (Date.now() > deadline) {
      const details = [...pending.keys()]
        .map((name) => `--- ${name} ---\n${tail(pending.get(name).logPath)}`)
        .join('\n');
      throw new Error(`timed out after ${timeoutMs}ms waiting for: ${[...pending.keys()].join(', ')}\n${details}`);
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 2_000));
  }
}

function stopServices(states) {
  log('==> Stopping services');
  for (const state of states) {
    if (state.exited) continue;
    if (process.platform === 'win32') {
      spawnSync('taskkill', ['/PID', String(state.child.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      try {
        process.kill(-state.child.pid, 'SIGTERM');
      } catch {
        state.child.kill('SIGTERM');
      }
    }
  }
}

function generateSecrets() {
  return {
    // 48 random bytes satisfy HMAC-SHA256 key length requirements and exceed any service minimum.
    jwtSecret: randomBytes(48).toString('base64url'),
    dbPassword: randomBytes(18).toString('base64url'),
  };
}

function ensureJars(services, build) {
  if (build) {
    log('==> Packaging backend jars (mvn -B -ntp -DskipTests package)');
    // Windows exposes Maven as `mvn.cmd`, which spawnSync cannot resolve without a shell.
    const maven = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
    run(maven, ['-B', '-ntp', '-DskipTests', 'package'], {
      label: 'mvn package',
      // Node refuses to execute .cmd files without a shell on Windows.
      shell: process.platform === 'win32',
    });
    return;
  }
  const missing = services.filter(([, service]) => !existsSync(jarFor(service)));
  if (missing.length > 0) {
    throw new Error(
      `missing jars: ${missing.map(([name]) => name).join(', ')} — rerun with --build`,
    );
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return 0;
  }

  const secrets = generateSecrets();
  const services = selectedServices(options.profile);
  const states = [];
  let infraStarted = false;
  let exitCode = 0;

  rmSync(LOG_DIR, { recursive: true, force: true });
  mkdirSync(LOG_DIR, { recursive: true });

  try {
    ensureJars(services, options.build);
    startInfrastructure(secrets);
    infraStarted = true;

    log(`==> Starting services (profile: ${options.profile})`);
    for (const [name, service] of services) {
      states.push(startService(name, service, secrets));
    }

    log('==> Waiting for readiness');
    await waitForServices(states);

    const gateway = `http://127.0.0.1:${SERVICES['api-gateway'].port}`;
    const serviceUrls = Object.fromEntries(
      services.map(([name, service]) => [name, `http://127.0.0.1:${service.port}`]),
    );

    log('');
    log(`==> Running journeys against ${gateway}`);
    const summary = await runJourneys({ gateway, secret: secrets.jwtSecret, serviceUrls });

    writeFileSync(
      join(LOG_DIR, 'journeys.json'),
      `${JSON.stringify(
        {
          profile: options.profile,
          generatedAt: new Date().toISOString(),
          passed: summary.passed,
          failed: summary.failed,
          skipped: summary.skipped,
          results: summary.results.map(({ name, ok, skipped, ms, error }) => ({
            name,
            ok,
            skipped: Boolean(skipped),
            ms,
            error: error?.message,
          })),
        },
        null,
        2,
      )}\n`,
    );

    exitCode = summary.failed > 0 ? 1 : 0;
  } catch (error) {
    console.error(`\nE2E run failed: ${error.message}`);
    exitCode = 1;
  } finally {
    if (!options.keep) {
      stopServices(states);
      if (infraStarted) stopInfrastructure(secrets);
    } else {
      log('');
      log('==> --keep set: services and containers are still running');
      log(`    gateway: http://127.0.0.1:${SERVICES['api-gateway'].port}`);
      log('    stop with: docker compose -f testing/e2e/docker-compose.e2e.yml down -v');
    }
    log(`Logs: ${LOG_DIR}`);
  }

  return exitCode;
}

main()
  .then((code) => {
    process.exitCode = code;
  })
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
