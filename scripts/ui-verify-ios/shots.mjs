// Captures of the iOS app by scenario, reached by deep link (no taps), in light and dark and at
// the default and a large text size.
//
//   node shots.mjs [--scenario baseline] [--theme light|dark|both] [--text default|large|both]
//                  [--device "iPhone 17"] [--run NAME] [--list]
//
// The app must be installed (install.mjs) and signed in to the seeded server (the desktop
// harness' Docker Vikunja; on the simulator it is http://localhost:3456). Captures land in
// out/<run>/<device>/<theme>-<text>-<step>.png. The simulator's appearance, text size and status
// bar are put back afterwards. A step that cannot run prints FAIL and the run exits 1.
import { join } from 'node:path'
import { HERE, argValue, resolveSimulator, seedIds } from './sim.mjs'

const ids = seedIds()
const project = ids?.work ?? 4
const task = ids?.tasks?.roadmap ?? 3

// A step opens a link; `fresh` first starts the app without its restored screen and sheets
// (an editor or the task entry stays open over every later link).
const SCENARIOS = {
  baseline: [
    { name: 'today', path: 'view/TODAY', fresh: true },
    { name: 'inbox', path: 'view/INBOX' },
    { name: 'upcoming', path: 'view/UPCOMING' },
    { name: 'anytime', path: 'view/ANYTIME' },
    { name: 'routines', path: 'view/ROUTINES' },
    { name: 'project', path: `view/PROJECT/${project}` },
    { name: 'quick-add', path: 'add' },
    { name: 'editor', path: `task/${task}`, fresh: true },
  ],
  lists: [
    { name: 'today', path: 'view/TODAY', fresh: true },
    { name: 'upcoming', path: 'view/UPCOMING' },
    { name: 'anytime', path: 'view/ANYTIME' },
  ],
  editor: [
    { name: 'editor', path: `task/${task}`, fresh: true },
  ],
}

const TEXT_SIZES = { default: 'large', large: 'accessibility-extra-large' }

const args = process.argv.slice(2)
if (args.includes('--list')) {
  for (const [name, steps] of Object.entries(SCENARIOS)) console.log(`${name}: ${steps.map((s) => s.name).join(', ')}`)
  process.exit(0)
}
const scenario = argValue(args, '--scenario', 'baseline')
const steps = SCENARIOS[scenario]
if (!steps) throw new Error(`Unknown scenario ${scenario}; --list shows them`)
const pick = (value, both) => (value === 'both' ? both : [value])
const themes = pick(argValue(args, '--theme', 'light'), ['light', 'dark'])
const texts = pick(argValue(args, '--text', 'default'), ['default', 'large'])
const sim = resolveSimulator(argValue(args, '--device'))
const out = join(HERE, 'out', argValue(args, '--run', scenario), sim.slug)

sim.boot()
if (!sim.isInstalled()) throw new Error(`Vicu Debug is not installed on ${sim.name}: run install.mjs first`)
if (!ids) console.log('WARN no seed-ids.json (VICU_DESKTOP_DIR): using the default seeded ids')

const before = { appearance: sim.appearance(), contentSize: sim.contentSize() }
let failed = 0
try {
  sim.fixStatusBar()
  for (const theme of themes) {
    for (const text of texts) {
      sim.appearance(theme)
      sim.contentSize(TEXT_SIZES[text])
      // The text size is read when the app starts.
      let started = false
      for (const step of steps) {
        try {
          if (step.fresh || !started) await sim.freshLaunch()
          started = true
          await sim.open(step.path)
          const file = sim.screenshot(join(out, `${theme}-${text}-${step.name}.png`))
          console.log(`ok   ${theme} ${text} ${step.name} -> ${file}`)
        } catch (e) {
          failed++
          console.log(`FAIL ${theme} ${text} ${step.name}: ${e.message}`)
        }
      }
    }
  }
} finally {
  sim.appearance(before.appearance === 'unsupported' ? 'light' : before.appearance)
  sim.contentSize(before.contentSize)
  sim.fixStatusBar(false)
}
process.exit(failed ? 1 : 0)
