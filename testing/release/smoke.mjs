#!/usr/bin/env node
/**
 * Release smoke test: production configuration, real infrastructure, no seed data.
 *
 * Unlike testing/e2e (which runs the `seed` profile and exercises business journeys), this script
 * validates the *release* configuration:
 *
 *   - the repository's own `docker-compose.yml` starts and becomes healthy;
 *   - all nine services boot from their executable jars with NO Spring profile and generated
 *     credentials (i.e. the same way they are deployed); `--services=core` starts auth, patient,
 *     doctor and the gateway on memory-constrained machines;
 *   - health endpoints are UP and hide details;
 *   - demo/seed accounts are absent, so the dev seeders never ran;
 *   - `/internal/**` is not reachable through the gateway and demands a credential directly;
 *   - the public contract works end to end through the gateway (register -> login -> me ->
 *     create patient profile -> duplicate registration rejected);
 *   - a service can be restarted and still serve previously persisted data (recovery);
 *   - on Linux/macOS a SIGTERM performs a graceful shutdown within the configured window.
 *
 *   node testing/release/smoke.mjs            # uses existing jars
 *   node testing/release/smoke.mjs --build    # packages first
 *   node testing/release/smoke.mjs --keep     # leaves the stack up for inspection
 *
 * Ports: gateway 17080, services 1708x, infrastructure on 54322/63792/59092 (see
 * docker-compose.release.yml, which overrides the published ports of the shipped compose file).
 */
import { spawn, spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { expectStatus, httpRequest, unwrap } from '../e2e/lib/http.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..');
const LOG_DIR = join(HERE, '.logs');

const DB_HOST = '127.0.0.1';
// Dedicated host ports: see docker-compose.release.yml. A native PostgreSQL on 5432 (common on
// Windows) must not be able to intercept the release smoke run.
const DB_PORT = Number(process.env.RELEASE_DB_PORT ?? 54322);
const REDIS_PORT = Number(process.env.RELEASE_REDIS_PORT ?? 63792);
const KAFKA_PORT = Number(process.env.RELEASE_KAFKA_PORT ?? 59092);

const COMPOSE_ARGS = [
  'compose',
  '-f',
  'docker-compose.yml',
  '-f',
  'testing/release/docker-compose.release.yml',
];

const SERVICES = {
  'auth-service': { port: 17081, database: 'mediflow_auth_db' },
  'patient-service': { port: 17083, database: 'patient_db' },
  'appointment-service': { port: 17084, database: 'appointment_db' },
  'doctor-service': { port: 17085, database: 'doctor_db' },
  'billing-service': { port: 17087, database: 'billing_db' },
  'analytics-service': { port: 17088, database: 'analytics_db' },
  'notification-service': { port: 17089, database: 'notification_db' },
  'medical-record-service': { port: 17091, database: 'medical_record_db' },
  'api-gateway': { port: 17080, database: null },
};

const JAVA_OPTIONS = [
  '-Xmx192m',
  '-XX:MaxMetaspaceSize=128m',
  '-XX:ReservedCodeCacheSize=48m',
  '-XX:CompressedClassSpaceSize=64m',
  '-Xss512k',
  '-XX:+UseSerialGC',
  '-XX:TieredStopAtLevel=1',
  '-Duser.timezone=UTC',
];

/**
 * Services start in small waves: nine Spring Boot contexts starting at once can exhaust memory on a
 * 7 GB CI runner or laptop. Readiness is awaited after each wave, so the peak stays bounded.
 */
const START_WAVES = [
  ['auth-service', 'patient-service', 'doctor-service'],
  ['appointment-service', 'medical-record-service', 'billing-service'],
  ['notification-service', 'analytics-service', 'api-gateway'],
];

/** Minimal set for constrained machines; the release contract itself is the same. */
const CORE_SERVICES = ['auth-service', 'patient-service', 'doctor-service', 'api-gateway'];

const results = [];
let checks = 0;

function log(message = '') {
  console.log(message);
}

function parseArgs(argv) {
  const options = { build: false, keep: false, help: false, services: 'all' };
  for (const arg of argv) {
    if (arg === '--build') options.build = true;
    else if (arg === '--keep') options.keep = true;
    else if (arg.startsWith('--services=')) options.services = arg.slice('--services='.length);
    else if (arg === '-h' || arg === '--help') options.help = true;
    else throw new Error(`unknown argument: ${arg}`);
  }
  if (!['all', 'core'].includes(options.services)) {
    throw new Error(`unknown --services value "${options.services}" (expected all or core)`);
  }
  return options;
}

function printHelp() {
  log(`MediFlow release smoke test

  node testing/release/smoke.mjs [options]

  --build   run "mvn -B -ntp -DskipTests package" first
  --keep    leave the services and containers running
  --services=all|core
            all (default) starts the nine services; core starts auth, patient, doctor and gateway
            for memory-constrained machines
  -h, --help

Requires: Docker + Compose v2, JDK 21, Node 24, free ports 54322/63792/59092 and 17080-17091.`);
}

async function check(name, fn) {
  checks += 1;
  const started = Date.now();
  try {
    await fn();
    results.push({ name, ok: true, ms: Date.now() - started });
    log(`  PASS  ${name}`);
  } catch (error) {
    results.push({ name, ok: false, ms: Date.now() - started, error });
    log(`  FAIL  ${name}`);
    log(`        ${error.message}`);
  }
}

function skip(name, reason) {
  results.push({ name, ok: true, skipped: true });
  log(`  SKIP  ${name} (${reason})`);
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: ROOT,
    env: options.env ?? process.env,
    stdio: options.stdio ?? 'inherit',
    shell: options.shell ?? false,
  });
  if (result.error) throw new Error(`${options.label ?? command} failed to start: ${result.error.message}`);
  if (result.status !== 0) throw new Error(`${options.label ?? command} exited with code ${result.status}`);
}

function composeEnvironment(secrets) {
  return {
    ...process.env,
    POSTGRES_USER: 'postgres',
    POSTGRES_PASSWORD: secrets.dbPassword,
  };
}

function stopInfrastructure(secrets) {
  spawnSync('docker', [...COMPOSE_ARGS, 'down', '-v', '--remove-orphans'], {
    cwd: ROOT,
    env: composeEnvironment(secrets),
    stdio: 'ignore',
  });
}

function startInfrastructure(secrets) {
  log(`==> Starting infrastructure from docker-compose.yml (db :${DB_PORT}, redis :${REDIS_PORT}, kafka :${KAFKA_PORT})`);
  stopInfrastructure(secrets);
  run('docker', [...COMPOSE_ARGS, 'up', '-d', '--wait', '--wait-timeout', '180'], {
    env: composeEnvironment(secrets),
    label: 'docker compose up',
  });
}

function jdbcUrl(database) {
  return `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${database}`;
}

function serviceEnvironment(secrets) {
  const env = {
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
    CORS_ALLOWED_ORIGINS: 'http://localhost:3000',
  };
  // Release configuration never activates a Spring profile.
  delete env.SPRING_PROFILES_ACTIVE;
  for (const [name, service] of Object.entries(SERVICES)) {
    if (name === 'api-gateway') continue;
    env[`${name.replace(/-/g, '_').toUpperCase()}_URL`] = `http://127.0.0.1:${service.port}`;
  }
  env.GATEWAY_SELF_URL = `http://127.0.0.1:${SERVICES['api-gateway'].port}`;
  return env;
}

function jarFor(name) {
  return join(ROOT, 'backend', name, 'target', `${name}-1.0.0-exec.jar`);
}

function startService(name, secrets) {
  const service = SERVICES[name];
  const jar = jarFor(name);
  if (!existsSync(jar)) throw new Error(`missing ${jar} — run with --build first`);

  const logPath = join(LOG_DIR, `${name}.log`);
  const logFd = openSync(logPath, 'w');
  const java = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const args = [
    ...JAVA_OPTIONS,
    '-jar',
    jar,
    `--server.port=${service.port}`,
    '--management.endpoint.health.probes.enabled=true',
  ];

  const child = spawn(java, args, {
    cwd: ROOT,
    env: {
      ...serviceEnvironment(secrets),
      ...(service.database ? { DB_URL: jdbcUrl(service.database) } : {}),
    },
    detached: process.platform !== 'win32',
    windowsHide: true,
    stdio: ['ignore', logFd, logFd],
  });
  closeSync(logFd);

  const state = { name, child, logPath, exited: false, exitCode: null };
  child.on('exit', (code, signal) => {
    state.exited = true;
    state.exitCode = code ?? signal;
  });
  child.unref();
  log(`    started ${name.padEnd(23)} http://127.0.0.1:${service.port}`);
  return state;
}

function tail(path, lines = 30) {
  try {
    return readFileSync(path, 'utf8').split(/\r?\n/).slice(-lines).join('\n');
  } catch {
    return '(no log)';
  }
}

async function isHealthy(name) {
  try {
    const response = await fetch(`http://127.0.0.1:${SERVICES[name].port}/actuator/health`, {
      signal: AbortSignal.timeout(3_000),
      headers: { accept: 'application/json' },
    });
    if (response.status !== 200) return false;
    return (await response.json())?.status === 'UP';
  } catch {
    return false;
  }
}

async function waitForHealthy(names, timeoutMs = 240_000) {
  const pending = new Set(names);
  const deadline = Date.now() + timeoutMs;
  while (pending.size > 0) {
    for (const name of [...pending]) {
      if (await isHealthy(name)) {
        pending.delete(name);
        log(`    ready   ${name}`);
      }
    }
    if (pending.size === 0) return;
    if (Date.now() > deadline) {
      const details = [...pending]
        .map((name) => `--- ${name} ---\n${tail(join(LOG_DIR, `${name}.log`))}`)
        .join('\n');
      throw new Error(`timed out waiting for: ${[...pending].join(', ')}\n${details}`);
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 2_000));
  }
}

/** Force-stops a service (used on Windows, where Node cannot deliver SIGTERM). */
function killService(state) {
  if (state.exited) return;
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

function waitForExit(state, timeoutMs) {
  return new Promise((resolvePromise) => {
    if (state.exited) {
      resolvePromise({ elapsedMs: 0, code: state.exitCode });
      return;
    }
    const started = Date.now();
    const timer = setInterval(() => {
      if (state.exited || Date.now() - started > timeoutMs) {
        clearInterval(timer);
        resolvePromise({ elapsedMs: Date.now() - started, code: state.exitCode });
      }
    }, 250);
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return 0;
  }

  const secrets = {
    jwtSecret: randomBytes(48).toString('base64url'),
    dbPassword: randomBytes(18).toString('base64url'),
  };
  const gateway = `http://127.0.0.1:${SERVICES['api-gateway'].port}`;
  const states = new Map();
  let infraStarted = false;

  rmSync(LOG_DIR, { recursive: true, force: true });
  mkdirSync(LOG_DIR, { recursive: true });

  try {
    if (options.build) {
      log('==> Packaging backend jars');
      run(process.platform === 'win32' ? 'mvn.cmd' : 'mvn', ['-B', '-ntp', '-DskipTests', 'package'], {
        label: 'mvn package',
        shell: process.platform === 'win32',
      });
    }

    startInfrastructure(secrets);
    infraStarted = true;

    log('==> Starting services without a Spring profile (release configuration)');
    const selected = new Set(options.services === 'core' ? CORE_SERVICES : Object.keys(SERVICES));
    for (const wave of START_WAVES) {
      const names = wave.filter((name) => selected.has(name));
      if (names.length === 0) continue;
      for (const name of names) {
        states.set(name, startService(name, secrets));
      }
      log('==> Waiting for readiness');
      await waitForHealthy(names);
    }

    // ------------------------------------------------------------------ health + config
    await check('every service reports UP and hides health details', async () => {
      for (const name of states.keys()) {
        const response = await httpRequest(`http://127.0.0.1:${SERVICES[name].port}`, 'GET', '/actuator/health');
        expectStatus(response, 200, `${name} health`);
        if (response.json?.status !== 'UP') throw new Error(`${name} health is ${response.json?.status}`);
        if (response.json?.components) throw new Error(`${name} exposes health details`);
      }
    });

    await check('demo seed accounts do not exist', async () => {
      const login = await httpRequest(gateway, 'POST', '/api/v1/auth/login', {
        body: { usernameOrEmail: 'patient', password: 'Patient@123' },
      });
      expectStatus(login, 401, 'seed patient login (must be rejected)');
    });

    await check('anonymous access to the public API is rejected', async () => {
      const me = await httpRequest(gateway, 'GET', '/api/v1/auth/me');
      expectStatus(me, 401, 'anonymous /auth/me');
    });

    // ------------------------------------------------------------------ internal surface
    await check('internal endpoints are not routed and require a credential', async () => {
      const status = await httpRequest(
        gateway,
        'GET',
        '/internal/doctors/00000000-0000-0000-0000-000000000002/schedule/validation?startTime=2035-01-01T09:00:00&endTime=2035-01-01T09:30:00',
      );
      expectStatus(status, [404, 403, 401], 'gateway /internal route');

      const direct = await httpRequest(
        `http://127.0.0.1:${SERVICES['doctor-service'].port}`,
        'GET',
        '/internal/doctors/00000000-0000-0000-0000-000000000002/schedule/validation?startTime=2035-01-01T09:00:00&endTime=2035-01-01T09:30:00',
      );
      expectStatus(direct, 401, 'doctor-service /internal without a credential');
    });

    // ------------------------------------------------------------------ public contract
    const username = `smoke${Date.now().toString(36)}`;
    // Password policy: letters AND digits, must not contain the username.
    const password = `Smoke7${randomBytes(9).toString('hex')}a1`;
    let accessToken = null;

    await check('a new account can register, log in and read its profile', async () => {
      const register = await httpRequest(gateway, 'POST', '/api/v1/auth/register', {
        body: {
          username,
          email: `${username}@smoke.test`,
          password,
          fullName: 'Release Smoke',
          phone: '555-0100',
          role: 'PATIENT',
        },
      });
      expectStatus(register, [200, 201], 'register');

      const login = await httpRequest(gateway, 'POST', '/api/v1/auth/login', {
        body: { usernameOrEmail: username, password },
      });
      expectStatus(login, 200, 'login');
      const data = unwrap(login, 'login');
      if (!data?.accessToken) throw new Error('login did not return an access token');
      accessToken = data.accessToken;

      const me = await httpRequest(gateway, 'GET', '/api/v1/auth/me', { token: accessToken });
      expectStatus(me, 200, '/auth/me');
      const profile = unwrap(me, '/auth/me');
      if (profile.username !== username) throw new Error(`profile username is ${profile.username}`);
      if (!profile.roles?.includes('PATIENT')) throw new Error('new account does not hold PATIENT');
    });

    await check('a patient can create their own profile through the gateway', async () => {
      const create = await httpRequest(gateway, 'POST', '/api/v1/patients', {
        token: accessToken,
        body: {
          firstName: 'Release',
          lastName: 'Smoke',
          email: `${username}@smoke.test`,
          phone: '555-0100',
          dateOfBirth: '1990-01-01',
        },
      });
      expectStatus(create, 201, 'create patient profile');

      const list = await httpRequest(gateway, 'GET', '/api/v1/patients?page=0&size=10', {
        token: accessToken,
      });
      expectStatus(list, 200, 'list own patient profile');
      const page = unwrap(list, 'list own patient profile');
      if (!Array.isArray(page.content) || page.content.length !== 1) {
        throw new Error(`expected exactly one patient profile, received ${page.content?.length}`);
      }
    });

    await check('duplicate registrations are rejected by the identity contract', async () => {
      const duplicate = await httpRequest(gateway, 'POST', '/api/v1/auth/register', {
        body: {
          username,
          email: `${username}@smoke.test`,
          password,
          fullName: 'Release Smoke',
          role: 'PATIENT',
        },
      });
      expectStatus(duplicate, [400, 409], 'duplicate registration');
      if (!/already exists/i.test(String(duplicate.json?.message ?? ''))) {
        throw new Error(`unexpected duplicate-registration message: ${duplicate.json?.message}`);
      }
    });

    // ------------------------------------------------------------------ shutdown + recovery
    await check('a restarted service recovers and keeps persisted data', async () => {
      const patient = states.get('patient-service');
      killService(patient);
      await waitForExit(patient, 30_000);
      if (patient.exited === false) throw new Error('patient-service did not stop');

      const restarted = startService('patient-service', secrets);
      states.set('patient-service', restarted);
      await waitForHealthy(['patient-service'], 180_000);

      const list = await httpRequest(gateway, 'GET', '/api/v1/patients?page=0&size=10', {
        token: accessToken,
      });
      expectStatus(list, 200, 'list patient profile after restart');
      const page = unwrap(list, 'list patient profile after restart');
      if (page.content?.length !== 1) throw new Error('persisted patient profile is missing after restart');
    });

    if (process.platform === 'win32') {
      skip(
        'SIGTERM performs a graceful shutdown',
        'Windows cannot deliver SIGTERM to a console JVM; covered by Compose/Docker smoke runs on Linux',
      );
    } else {
      await check('SIGTERM performs a graceful shutdown within the configured window', async () => {
        const doctor = states.get('doctor-service');
        process.kill(-doctor.child.pid, 'SIGTERM');
        const { elapsedMs, code } = await waitForExit(doctor, 35_000);
        if (!doctor.exited) throw new Error(`doctor-service did not stop within 35s (waited ${elapsedMs}ms)`);
        if (![0, 143].includes(code)) throw new Error(`unexpected exit code ${code}`);
        if (elapsedMs > 35_000) throw new Error(`graceful shutdown took ${elapsedMs}ms`);
      });
    }

    writeFileSync(
      join(LOG_DIR, 'smoke.json'),
      `${JSON.stringify(
        {
          generatedAt: new Date().toISOString(),
          checks,
          failed: results.filter((result) => !result.ok).length,
          skipped: results.filter((result) => result.skipped).length,
          results: results.map(({ name, ok, skipped, ms, error }) => ({
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
  } catch (error) {
    log(`\nRelease smoke failed: ${error.message}`);
    results.push({ name: 'smoke run', ok: false, error });
  } finally {
    if (!options.keep) {
      log('==> Stopping services and infrastructure');
      for (const state of states.values()) killService(state);
      if (infraStarted) stopInfrastructure(secrets);
    } else {
      log('');
      log('==> --keep set: services and containers are still running');
      log(`    gateway: ${gateway}`);
      log('    stop with: docker compose -f docker-compose.yml -f testing/release/docker-compose.release.yml down -v');
    }
    log(`Logs: ${LOG_DIR}`);
  }

  const failed = results.filter((result) => !result.ok);
  const skipped = results.filter((result) => result.skipped).length;
  const executed = results.length - skipped;
  log('');
  log(
    `Release smoke: ${executed - failed.length}/${executed} checks passed, ${failed.length} failed, ${skipped} skipped`,
  );
  return failed.length === 0 ? 0 : 1;
}

main()
  .then((code) => {
    process.exitCode = code;
  })
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
