import vorpal from 'vorpal'
// import { words } from 'lodash'
import chalk from 'chalk'
import { connect } from 'net'
import { Message } from './Message'

export const cli = vorpal()

let username
let server
let lastCommand
let lastDirectMessage

cli
  .delimiter(cli.chalk['yellow']('ftd~$'))

cli
  .mode('connect <username>')
  .delimiter(cli.chalk['green']('connected>'))
  .init(function (args, callback) {
    username = args.username
    server = connect({ host: 'localhost', port: 8080 }, () => {
      server.write(new Message({ username, command: 'connect' }).toJSON() + '\n')
      callback()
    })

    server.on('data', (buffer) => {
      let { command, contents } = Message.fromJSON(buffer)
      switch (command) {
        case 'connect':
          this.log(chalk.green(contents))
          break
        case 'disconnect':
          this.log(chalk.red(contents))
          break
        case 'echo':
          this.log(chalk.magenta(contents))
          break
        case 'users':
          this.log(chalk.yellow(contents))
          break
        case 'direct message':
          this.log(chalk.blue(contents))
          break
        case 'broadcast':
          this.log(chalk.white(contents))
          break
        default:
          this.log(contents)
          break
      }
    })

    server.on('end', () => {
      cli.exec('exit')
    })
  })
  .action(function (input, callback) {
    const [ command, ...rest ] = input.split(' ')
    const contents = rest.join(' ')
    let lastCommandValid = true

    switch (command) {
      case ('disconnect'):
        server.end(new Message({ username, command }).toJSON() + '\n')
        break
      case ('echo'):
        lastCommand = command
        server.write(new Message({ username, command, contents }).toJSON() + '\n')
        break
      case ('users'):
        lastCommand = command
        server.write(new Message({ username, command, contents }).toJSON() + '\n')
        break
      case ('broadcast'):
        lastCommand = command
        server.write(new Message({ username, command, contents }).toJSON() + '\n')
        break
      default:
        if (command.charAt(0) === '@') {
          let newContents = command.substring(1) + ' ' + contents
          server.write(new Message({ username, command: 'direct message', contents: newContents }).toJSON() + '\n')
          lastCommand = command
        } else if (lastCommand) {
          if (lastCommand.charAt(0) === '@') {
            let newContents = lastCommand.substring(1) + ' ' + command + ' ' + contents
            server.write(new Message({ username, command: 'direct message', contents: newContents }).toJSON() + '\n')
          } else {
            server.write(new Message({ username, command: lastCommand, contents: command + ' ' + contents }).toJSON() + '\n')
          }
        } else {
          this.log(`Command <${command}> was not recognized`)
        }
        lastCommandValid = false
        break
    }
    if (lastCommandValid) {
      lastCommand = command
    }
    callback()
  })
