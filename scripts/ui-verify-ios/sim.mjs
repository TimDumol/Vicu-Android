// simctl driver for the Vicu iOS UI harness.
//
// Device safety: only simulators. A device is picked by name or UDID and must be one that
// `xcrun simctl list devices available` lists; every call then names that UDID (never "booted"),
// so nothing here can reach a physical iPhone or iPad. Taps and typing are not done here: the
// screens are reached by deep link, and what needs a finger is done with the iOS Simulator tools.
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const HERE = dirname(fileURLToPath(import.meta.url))
export const REPO = resolve(HERE, '..', '..')
export const BUNDLE_ID = 'com.rendyhd.vicu.debug'
export const APP_GROUP = 'group.com.rendyhd.vicu.debug'
export const SCHEME = 'vicu-debug'
export const DEFAULT_DEVICE = 'iPhone 17'

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** `vicu-debug://<path>`: the debug build's links (AppLink in the shared code). */
export const link = (path) => `${SCHEME}://${path}`

export function simctl(...args) {
  return run('xcrun', ['simctl', ...args.map(String)])
}

export function run(cmd, args, input) {
  const r = spawnSync(cmd, args, { maxBuffer: 64 * 1024 * 1024, input })
  if (r.error || r.status !== 0) {
    const err = (r.stderr?.toString() || r.error?.message || '').trim().split('\n').slice(-1)[0]
    throw new Error(`${cmd} ${args.slice(0, 4).join(' ')} failed (exit ${r.status}): ${err}`)
  }
  return r.stdout.toString()
}

const UDID = /^[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}$/i

/** The available simulators: { udid, name, state, runtime }. */
export function simulators() {
  const { devices } = JSON.parse(simctl('list', 'devices', 'available', '-j'))
  return Object.entries(devices).flatMap(([runtime, list]) =>
    list.map((d) => ({ udid: d.udid, name: d.name, state: d.state, runtime })))
}

/**
 * The simulator named [nameOrUdid] (default: VICU_SIM, else iPhone 17): a booted one first, then
 * the newest runtime. Throws for anything that is not a listed simulator.
 */
export function resolveSimulator(nameOrUdid = process.env.VICU_SIM || DEFAULT_DEVICE) {
  const list = simulators()
  const wanted = String(nameOrUdid)
  const found = UDID.test(wanted)
    ? list.filter((d) => d.udid.toLowerCase() === wanted.toLowerCase())
    : list.filter((d) => d.name === wanted)
  found.sort((a, b) => (b.state === 'Booted') - (a.state === 'Booted') || b.runtime.localeCompare(a.runtime))
  if (!found.length) throw new Error(`No simulator named or with UDID "${wanted}" (only simulators are used)`)
  return new Simulator(found[0])
}

export class Simulator {
  constructor({ udid, name, runtime }) {
    this.udid = udid
    this.name = name
    this.runtime = runtime
  }

  get slug() {
    return this.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
  }

  get booted() {
    return simulators().find((d) => d.udid === this.udid)?.state === 'Booted'
  }

  boot() {
    if (!this.booted) simctl('boot', this.udid)
    simctl('bootstatus', this.udid, '-b')
  }

  install(appPath) {
    simctl('install', this.udid, appPath)
  }

  isInstalled() {
    try {
      simctl('get_app_container', this.udid, BUNDLE_ID)
      return true
    } catch {
      return false
    }
  }

  launch() {
    simctl('launch', this.udid, BUNDLE_ID)
  }

  terminate() {
    try {
      simctl('terminate', this.udid, BUNDLE_ID)
    } catch {
      // Not running.
    }
  }

  /**
   * Starts the app without the UI state iOS would restore (the screen and sheets of the last
   * run): it is ended, its saved scene state removed, and started again.
   */
  async freshLaunch(waitMs = 6000) {
    this.terminate()
    const data = simctl('get_app_container', this.udid, BUNDLE_ID, 'data').trim()
    rmSync(join(data, 'Library', 'Saved Application State', `${BUNDLE_ID}.savedState`), { recursive: true, force: true })
    this.launch()
    await sleep(waitMs)
  }

  async open(path, waitMs = 2500) {
    simctl('openurl', this.udid, link(path))
    await sleep(waitMs)
  }

  screenshot(file) {
    mkdirSync(dirname(file), { recursive: true })
    simctl('io', this.udid, 'screenshot', '--type=png', file)
    return file
  }

  /** light or dark */
  appearance(mode) {
    if (mode === undefined) return simctl('ui', this.udid, 'appearance').trim()
    simctl('ui', this.udid, 'appearance', mode)
  }

  /** A UIContentSizeCategory name: large (the default), extra-extra-extra-large, accessibility-extra-large, ... */
  contentSize(size) {
    if (size === undefined) return simctl('ui', this.udid, 'content_size').trim()
    simctl('ui', this.udid, 'content_size', size)
  }

  /** The same status bar on every capture (9:41, full battery). */
  fixStatusBar(on = true) {
    if (on) {
      simctl('status_bar', this.udid, 'override', '--time', '9:41', '--batteryState', 'charged',
        '--batteryLevel', '100', '--wifiBars', '3', '--cellularMode', 'notSupported')
    } else {
      simctl('status_bar', this.udid, 'clear')
    }
  }

  /**
   * Puts the contents of [file] on the simulator's pasteboard without printing it, so a test
   * password or token can be pasted into a field with the iOS Simulator tools.
   */
  copyFileToPasteboard(file) {
    run('xcrun', ['simctl', 'pbcopy', this.udid], readFileSync(file))
  }

  /** The App Group folder the app shares with its widget and share extensions. */
  appGroup() {
    return simctl('get_app_container', this.udid, BUNDLE_ID, APP_GROUP).trim()
  }
}

/**
 * The seeded server's ids (seed-ids.json of the desktop harness: `node scripts/ui-verify/seed.mjs`
 * in the vicu repo), from VICU_DESKTOP_DIR or the sibling checkout; null when it was not seeded.
 */
export function seedIds() {
  const desktop = process.env.VICU_DESKTOP_DIR || resolve(REPO, '..', 'vicu')
  const file = join(desktop, 'scripts', 'ui-verify', '.local', 'seed-ids.json')
  return existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null
}

export function argValue(args, name, fallback) {
  const i = args.indexOf(name)
  return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback
}
