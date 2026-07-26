const fs = require('fs')
const path = require('path')
const { TextDecoder } = require('util')

const decoder = new TextDecoder('utf-8', { fatal: true })
const extensions = new Set(['.html', '.js', '.json', '.scss', '.vue'])
const roots = ['src', 'scripts', 'public', 'e2e']
const failures = []
const leadTodoRoots = [
  path.join('src', 'views', 'lead'),
  path.join('src', 'views', 'todo'),
  path.join('src', 'components', 'TodoDynamicForm'),
  path.join('src', 'api'),
  'e2e',
  'public',
  'scripts'
].map(value => path.resolve(__dirname, '..', value))
const mojibakeMarkers = [
  [0xFFFD],
  [0x00C3],
  [0x00C2],
  [0x00E7, 0x00BA],
  [0x00E5, 0x00BE],
  [0x00E6, 0x02C6],
  [0x00E8, 0x00B7],
  [0x00E9, 0x2026],
  [0x951F, 0x65A4, 0x62F7]
].map(codepoints => String.fromCodePoint(...codepoints))

function visit(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      visit(file)
    } else if (extensions.has(path.extname(entry.name))) {
      try {
        const source = decoder.decode(fs.readFileSync(file))
        if (leadTodoRoots.some(root => file === root || file.startsWith(`${root}${path.sep}`))) {
          const marker = mojibakeMarkers.find(value => source.includes(value))
          if (marker) failures.push(`${file}: contains mojibake marker ${JSON.stringify(marker)}`)
        }
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
