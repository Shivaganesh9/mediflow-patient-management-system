#!/usr/bin/env node
/**
 * Static release-readiness checks for MediFlow.
 *
 * These checks do not need Docker, a database or a network: they inspect the repository for the
 * guarantees a release depends on (production-safe configuration, migration ownership, secret
 * hygiene, image/Dockerfile contract, gateway exposure, dev-seed isolation).
 *
 *   node testing/release/check-release-readiness.mjs
 *
 * Exit code 0 means every check passed. The dynamic checks (containers, health, smoke journey)
 * live in `smoke.mjs`.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..');

const SERVICES = [
  'api-gateway',
  'auth-service',
  'patient-service',
  'doctor-service',
  'appointment-service',
  'medical-record-service',
  'billing-service',
  'notification-service',
  'analytics-service',
];

/** Services that own a database (the gateway does not). */
const DATA_SERVICES = SERVICES.filter((service) => service !== 'api-gateway');

const failures = [];
let checks = 0;

function check(name, fn) {
  checks += 1;
  try {
    fn();
    console.log(`  PASS  ${name}`);
  } catch (error) {
    failures.push({ name, message: error.message });
    console.log(`  FAIL  ${name}`);
    console.log(`        ${error.message}`);
  }
}

function read(path) {
  return readFileSync(join(ROOT, path), 'utf8');
}

function requireFile(path, hint = '') {
  if (!existsSync(join(ROOT, path))) throw new Error(`missing ${path}${hint ? ` (${hint})` : ''}`);
}

function assertContains(haystack, needle, label) {
  if (!haystack.includes(needle)) throw new Error(`${label} must contain ${JSON.stringify(needle)}`);
}

function assertNotMatches(haystack, pattern, label) {
  const match = haystack.match(pattern);
  if (match) throw new Error(`${label} must not match ${pattern} (found ${JSON.stringify(match[0])})`);
}

function assertMatches(haystack, pattern, label) {
  if (!pattern.test(haystack)) throw new Error(`${label} must match ${pattern}`);
}

function serviceYml(service) {
  return read(`backend/${service}/src/main/resources/application.yml`);
}

function walk(dir, predicate = () => true, out = []) {
  const absolute = join(ROOT, dir);
  if (!existsSync(absolute)) return out;
  for (const entry of readdirSync(absolute)) {
    if (entry === 'target' || entry === 'node_modules' || entry === '.git') continue;
    const child = join(absolute, entry);
    const stats = statSync(child);
    if (stats.isDirectory()) {
      walk(relative(ROOT, child), predicate, out);
    } else if (predicate(child)) {
      out.push(relative(ROOT, child).replace(/\\/g, '/'));
    }
  }
  return out;
}

function gitFiles() {
  return execFileSync('git', ['ls-files', '-co', '--exclude-standard'], {
    cwd: ROOT,
    encoding: 'utf8',
  })
    .split(/\r?\n/)
    .filter(Boolean);
}

const TEXT_FILE = /\.(java|kt|ts|tsx|js|mjs|json|yml|yaml|xml|sql|md|properties|env|example|sh|ps1|dockerfile|conf|toml)$/i;

console.log('MediFlow release readiness checks\n');

check('every runnable service ships a Dockerfile and .dockerignore', () => {
  for (const service of SERVICES) {
    requireFile(`backend/${service}/Dockerfile`);
    requireFile(`backend/${service}/.dockerignore`);
  }
});

check('Dockerfiles copy the executable (-exec) jar', () => {
  for (const service of SERVICES) {
    const dockerfile = read(`backend/${service}/Dockerfile`);
    assertContains(dockerfile, '-exec.jar', `${service} Dockerfile`);
  }
});

check('services are repackaged with the exec classifier', () => {
  for (const service of SERVICES) {
    const pom = read(`backend/${service}/pom.xml`);
    assertContains(pom, '<classifier>exec</classifier>', `${service} pom.xml`);
  }
});

check('schema changes are migration-owned (ddl-auto: validate)', () => {
  for (const service of DATA_SERVICES) {
    const yml = serviceYml(service);
    assertContains(yml, 'ddl-auto: validate', `${service} application.yml`);
    assertNotMatches(
      yml,
      /ddl-auto:\s*(update|create|create-drop)/,
      `${service} application.yml`,
    );
  }
});

check('Flyway is enabled in every data-owning service', () => {
  for (const service of DATA_SERVICES) {
    const yml = serviceYml(service);
    assertMatches(yml, /flyway:\s*\r?\n\s+enabled:\s*true/, `${service} application.yml`);
  }
});

check('notification/analytics own their migration directories', () => {
  for (const service of ['notification-service', 'analytics-service']) {
    const yml = serviceYml(service);
    assertMatches(
      yml,
      /locations:\s*classpath:db\/migration\/(notification|analytics)/,
      `${service} application.yml`,
    );
  }
});

check('every service shuts down gracefully', () => {
  for (const service of SERVICES) {
    const yml = serviceYml(service);
    assertContains(yml, 'shutdown: graceful', `${service} application.yml`);
    assertMatches(yml, /timeout-per-shutdown-phase/, `${service} application.yml`);
  }
});

check('health is exposed and sensitive actuator endpoints are not', () => {
  for (const service of SERVICES) {
    const yml = serviceYml(service);
    assertMatches(yml, /include:\s*[^\n]*health/, `${service} application.yml`);
    const exposure = yml.match(/include:\s*([^\n]+)/);
    for (const forbidden of ['env', 'heapdump', 'threaddump', 'configprops', 'beans', 'loggers']) {
      if (exposure && exposure[1].split(',').map((value) => value.trim()).includes(forbidden)) {
        throw new Error(`${service} exposes /actuator/${forbidden}`);
      }
    }
  }
});

check('.env.example documents the runtime contract', () => {
  const env = read('.env.example');
  for (const key of [
    'POSTGRES_USER',
    'POSTGRES_PASSWORD',
    'DB_USERNAME',
    'DB_PASSWORD',
    'JWT_SECRET',
    'CORS_ALLOWED_ORIGINS',
    'KAFKA_BOOTSTRAP_SERVERS',
    'REDIS_HOST',
    'REDIS_PORT',
    'AUTH_SERVICE_URL',
    'PATIENT_SERVICE_URL',
    'APPOINTMENT_SERVICE_URL',
    'DOCTOR_SERVICE_URL',
    'MEDICAL_RECORD_SERVICE_URL',
    'BILLING_SERVICE_URL',
    'NOTIFICATION_SERVICE_URL',
    'ANALYTICS_SERVICE_URL',
  ]) {
    assertMatches(env, new RegExp(`^${key}=`, 'm'), '.env.example');
  }
});

check('.env.example contains placeholders, not credentials', () => {
  const env = read('.env.example');
  const jwtSecret = env.match(/^JWT_SECRET=(.*)$/m)?.[1]?.trim() ?? '';
  if (jwtSecret.length > 0 && !/(change|replace|generate|secret)/i.test(jwtSecret)) {
    throw new Error(`JWT_SECRET in .env.example looks like a real secret (${jwtSecret.length} chars)`);
  }
  for (const key of ['DB_PASSWORD', 'POSTGRES_PASSWORD']) {
    const value = env.match(new RegExp(`^${key}=(.*)$`, 'm'))?.[1]?.trim() ?? '';
    if (!/^(change-me|replace-|<.*>)$/i.test(value)) {
      throw new Error(`${key} in .env.example is not an obvious placeholder`);
    }
  }
});

check('docker compose requires database credentials', () => {
  const compose = read('docker-compose.yml');
  assertContains(compose, '${POSTGRES_USER:?', 'docker-compose.yml');
  assertContains(compose, '${POSTGRES_PASSWORD:?', 'docker-compose.yml');
  assertContains(compose, 'stop_grace_period', 'docker-compose.yml');
});

check('compose variables are declared in .env.example', () => {
  const compose = read('docker-compose.yml');
  const env = read('.env.example');
  const declared = new Set(
    [...env.matchAll(/^([A-Z][A-Z0-9_]*)=/gm)].map((match) => match[1]),
  );
  const used = new Set(
    [...compose.matchAll(/\$\{([A-Z][A-Z0-9_]*)(?::[-?][^}]*)?\}/g)].map((match) => match[1]),
  );
  const missing = [...used].filter((key) => !declared.has(key));
  if (missing.length > 0) {
    throw new Error(`docker-compose.yml uses undeclared variables: ${missing.join(', ')}`);
  }
});

check('no application.yml activates the dev seed profile', () => {
  for (const service of SERVICES) {
    const yml = serviceYml(service);
    assertNotMatches(yml, /profiles:\s*\r?\n\s+active:\s*seed/, `${service} application.yml`);
    assertNotMatches(yml, /profiles:\s*\r?\n\s+include:\s*[^\n]*seed/, `${service} application.yml`);
  }
});

check('demo data seeders are gated by the seed profile', () => {
  const seeders = walk('backend', (file) => /src[\\/]main[\\/]java[\\/].*(Seed|Seeder)\.java$/.test(file));
  if (seeders.length === 0) throw new Error('no seeders found — did the layout change?');
  for (const file of seeders) {
    const source = read(file);
    if (!/@Profile\("seed"\)/.test(source)) {
      throw new Error(`${file} is not annotated with @Profile("seed")`);
    }
  }
});

check('demo credentials never appear in main resources', () => {
  const passwords = ['Admin@123', 'Doctor@123', 'Patient@123', 'Nurse@123', 'Billing@123', 'Support@123'];
  const resources = walk('backend', (file) => /src[\\/]main[\\/]resources[\\/]/.test(file));
  for (const file of resources) {
    const text = read(file);
    for (const password of passwords) {
      if (text.includes(password)) throw new Error(`${file} contains the demo password ${password}`);
    }
  }
});

check('the gateway does not route internal endpoints', () => {
  const gateway = serviceYml('api-gateway');
  if (/Path=[^\n]*\/internal/.test(gateway)) throw new Error('gateway routes /internal/**');
});

check('gateway routes stay within the public prefixes', () => {
  const gateway = serviceYml('api-gateway');
  // Only predicate lines (`- Path=...`); RewritePath filters also contain `Path=`.
  const predicates = [...gateway.matchAll(/^\s*-\s*Path=([^\n]+)$/gm)].map((match) => match[1].trim());
  if (predicates.length === 0) throw new Error('no gateway route predicates found');
  for (const predicate of predicates) {
    for (const path of predicate.split(',')) {
      if (!/^\/(api\/v1|v3\/api-docs|swagger-ui)/.test(path.trim())) {
        throw new Error(`unexpected gateway route prefix: ${path.trim()}`);
      }
    }
  }
});

check('gateway CORS is an explicit, configurable allow-list', () => {
  const gateway = serviceYml('api-gateway');
  assertMatches(gateway, /allowedOrigins:\s*\$\{CORS_ALLOWED_ORIGINS:/, 'gateway CORS');
  assertNotMatches(gateway, /allowedOrigins:\s*["']?\*/, 'gateway CORS');
  assertContains(gateway, 'allowCredentials: true', 'gateway CORS');
});

check('no private keys or provider tokens are committed', () => {
  const patterns = [
    /-----BEGIN (?:RSA |EC |OPENSSH |PGP )?PRIVATE KEY-----/,
    /\bAKIA[0-9A-Z]{16}\b/,
    /\bghp_[A-Za-z0-9]{36}\b/,
    /\bsk-[A-Za-z0-9]{32,}\b/,
    /\bAIza[0-9A-Za-z_-]{35}\b/,
  ];
  for (const file of gitFiles()) {
    if (!TEXT_FILE.test(file)) continue;
    if (file === '.env.example') continue;
    if (/(package-lock\.json|comprehensive-codebase-audit\.html)$/.test(file)) continue;
    const text = read(file);
    for (const pattern of patterns) {
      const match = text.match(pattern);
      if (match) throw new Error(`${file} contains something that looks like a credential (${match[0].slice(0, 24)}…)`);
    }
  }
});

check('the local .env file is ignored and untracked', () => {
  const ignore = read('.gitignore');
  assertMatches(ignore, /^\.env$/m, '.gitignore');
  let tracked = '';
  try {
    tracked = execFileSync('git', ['ls-files', '--error-unmatch', '.env'], {
      cwd: ROOT,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    });
  } catch {
    tracked = '';
  }
  if (tracked.trim()) throw new Error('.env is tracked by git');
});

check('each data-owning service has numbered Flyway migrations', () => {
  for (const service of DATA_SERVICES) {
    const dir = `backend/${service}/src/main/resources/db/migration`;
    const migrations = walk(dir, (file) => /V\d+__.*\.sql$/.test(file));
    if (migrations.length === 0) throw new Error(`${service} has no V*__*.sql migrations`);
    const versions = new Set();
    for (const file of migrations) {
      const version = file.match(/V(\d+)__/)[1];
      if (versions.has(version)) throw new Error(`${service} has duplicate migration version V${version}`);
      versions.add(version);
    }
    if (!versions.has('1')) throw new Error(`${service} has no V1 baseline migration`);
  }
});

check('frontend builds for release and never calls a service port directly', () => {
  const pkg = JSON.parse(read('frontend/mediflow-ui/package.json'));
  if (!pkg.scripts?.build) throw new Error('frontend package.json has no build script');
  const sources = walk('frontend/mediflow-ui/src', (file) => /\.(ts|tsx)$/.test(file));
  for (const file of sources) {
    const text = read(file);
    if (/localhost:(8081|8083|8084|8085|8087|8088|8089|8091)\b/.test(text)) {
      throw new Error(`${file} calls a backend service port directly instead of the gateway`);
    }
  }
});

check('CI builds every service image and runs both E2E suites', () => {
  const ci = read('.github/workflows/ci.yml');
  for (const service of SERVICES) {
    assertContains(ci, service, 'ci.yml image matrix');
  }
  assertContains(ci, 'playwright', 'ci.yml');
  assertContains(ci, 'testing/e2e/run-e2e.mjs', 'ci.yml');
  assertContains(ci, 'docker compose', 'ci.yml');
});

console.log('');
if (failures.length > 0) {
  console.log(`Release readiness: ${checks - failures.length}/${checks} checks passed, ${failures.length} failed`);
  process.exitCode = 1;
} else {
  console.log(`Release readiness: all ${checks} checks passed`);
}
