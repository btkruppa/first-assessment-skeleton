import vorpal from 'vorpal'
// import { words } from 'lodash'
import chalk from 'chalk'
import { connect } from 'net'
import { Message } from './Message'

export const cli = vorpal()

let username
let server
let host = 'localhost'
let port = 8080
let lastCommand

cli
  .delimiter(cli.chalk['yellow']('ftd~$'))

cli
  .mode('connect <username> [host] [port]')
  .delimiter(cli.chalk['green']('connected>'))
  .init(function (args, callback) {
    username = args.username
    if (args.host !== undefined) {
      host = args.host
      if (args.port !== undefined) {
        port = args.port
      }
    }
    try {
      server = connect({ host, port }, () => {
        server.write(new Message({ username, command: 'connect' }).toJSON() + '\n')
        callback()
      })//.catch( (error) => console.log('we caught it!')))

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
          case 'broadcast':
            this.log(chalk.white(contents))
            break
          default:
            if (command.charAt(0) === '@') {
              this.log(chalk.blue(contents))
            } else {
              this.log(contents)
            }
            break
        }
      })

      server.on('end', () => {
        cli.exec('exit')
      })
    } catch (e) {
      //console.log('failed to connect to server')
    }
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
        server.write(new Message({ username, command, contents }).toJSON() + '\n')
        break
      case ('users'):
        server.write(new Message({ username, command, contents }).toJSON() + '\n')
        break
      case ('broadcast'):
        server.write(new Message({ username, command, contents }).toJSON() + '\n')
        break
      default:
        if (command.charAt(0) === '@') {
          server.write(new Message({ username, command, contents }).toJSON() + '\n')
          break
        }

        if (lastCommand) {
          server.write(new Message({ username, command: lastCommand, contents: command + ' ' + contents }).toJSON() + '\n')
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
