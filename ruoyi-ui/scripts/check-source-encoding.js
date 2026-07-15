const fs = require('fs')
const path = require('path')
const { TextDecoder } = require('util')

const decoder = new TextDecoder('utf-8', { fatal: true })
const extensions = new Set(['.js', '.json', '.scss', '.vue'])
const roots = ['src', 'scripts']
const failures = []

function visit(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      visit(file)
    } else if (extensions.has(path.extname(entry.name))) {
      try {
        decoder.decode(fs.readFileSync(file))
      } catch (error) {
        failures.push(`${file}: ${error.message}`)
      }
    }
  }
}

for (const root of roots) visit(path.resolve(__dirname, '..', root))

if (failures.length) {
  console.error('Invalid UTF-8 source files:')
  failures.forEach(failure => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('All frontend source files are valid UTF-8.')
