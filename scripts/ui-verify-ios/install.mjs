// Builds the debug app for a simulator and installs it.
//
//   node install.mjs [--device "iPhone 17"] [--no-build] [--launch]
//
// The build is the iOS gate's (XcodeGen project, xcodebuild into iosApp/build/dd). It is then
// signed with your Apple Development certificate when there is one (iosApp/scripts/
// sign-for-simulator.sh): the Tasks widget can resolve its list only in a team-signed build.
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { REPO, argValue, resolveSimulator, run } from './sim.mjs'

const args = process.argv.slice(2)
const sim = resolveSimulator(argValue(args, '--device'))
const iosApp = join(REPO, 'iosApp')
const app = join(iosApp, 'build', 'dd', 'Build', 'Products', 'Debug-iphonesimulator', 'Vicu.app')

sim.boot()
if (!args.includes('--no-build')) {
  console.log(`Building for ${sim.name} (${sim.udid})...`)
  run(join(iosApp, 'scripts', 'generate.sh'), [])
  run('xcodebuild', [
    '-project', join(iosApp, 'Vicu.xcodeproj'), '-scheme', 'Vicu', '-configuration', 'Debug',
    '-destination', `platform=iOS Simulator,id=${sim.udid}`, '-derivedDataPath', join(iosApp, 'build', 'dd'),
    'build', '-quiet',
  ])
}
if (!existsSync(app)) throw new Error(`No build at ${app}`)
try {
  console.log(run(join(iosApp, 'scripts', 'sign-for-simulator.sh'), [app]).trim())
} catch (e) {
  console.log(`WARN not team-signed (${e.message}); the Tasks widget will always show Today`)
}
sim.install(app)
console.log(`Installed on ${sim.name}`)
if (args.includes('--launch')) sim.launch()
