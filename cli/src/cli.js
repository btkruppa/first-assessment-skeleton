import vorpal from 'vorpal'
// import { words } from 'lodash'
import { connect } from 'net'
import { Message } from './Message'
import dateFormat from 'dateformat'
import chalk from 'chalk'
import fs from 'fs'

export const cli = vorpal()

let username
let server
let host = 'localhost'
let port = 8080
let lastCommand
let replyCommand
let encryptVal = 0

function encrypt (someString) {
  if (encryptVal) {
    return someString.split('').map(char => char.charCodeAt(0) * encryptVal).join(' ')
  }
  return someString
}

cli
  .delimiter(cli.chalk['yellow']('ftd~$'))

cli
  .mode('connect <username> [host] [port]')
  .delimiter(cli.chalk['green']('connected>'))
  .init(function (args, callback) {
    this.log('type help for a list of commands')
    username = args.username
    if (args.host !== undefined) {
      host = args.host
      if (args.port !== undefined) {
        port = args.port
      }
    }
    server = connect({ host, port }, () => {
      server.write(new Message({ username, command: 'connect' }).toJSON() + '\n')
      callback()
    })

    server.on('data', (buffer) => {
      let { username, command, contents } = Message.fromJSON(buffer)
      if (command.charAt(0) === '@') {
        this.log(chalk.blue(contents))
        replyCommand = '@' + username
      } else {
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
            this.log(contents)
            break
        }
      }
      if (encryptVal) {
        // my own super secret highly advanced encryption algoriths, haha nah pretty basic encryption
        let encryptedContentsArr = contents.split(' ')
        let unencryptedContentsArr = encryptedContentsArr.map(encChar => String.fromCharCode(encChar/encryptVal))
        if (command === 'echo' || command === 'broadcast' || command.charAt(0) === '@') {
          this.log(chalk.cyan(unencryptedContentsArr.join('')))
        }
      }
    })

    server.on('end', () => {
      cli.exec('exit')
    })
  })
  .action(function (input, callback) {
    const [ command, ...rest ] = input.split(' ')
    const contents = rest.join(' ')
    let newContents = ''

    switch (command) {
      case ('disconnect'):
        server.end(new Message({ username, command }).toJSON() + '\n')
        break
      case ('echo'):
        newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (echo): ${contents}`
        newContents = encrypt(newContents)
        server.write(new Message({ username, command, contents: newContents }).toJSON() + '\n')
        lastCommand = command
        break
      case ('users'):
        server.write(new Message({ username, command, contents: newContents }).toJSON() + '\n')
        lastCommand = command
        break
      case ('broadcast'):
        newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (all): ${contents}`
        newContents = encrypt(newContents)
        server.write(new Message({ username, command, contents: newContents }).toJSON() + '\n')
        lastCommand = command
        break
      case ('/r'):
      // once initiated in a reply cycle, the repeat will continue to send to that user until you type /r again which will update to most recent user or another valid command
        if (replyCommand) {
          newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (whisper): ${contents}`
          newContents = encrypt(newContents)
          server.write(new Message({ username, command: replyCommand, contents: newContents }).toJSON() + '\n')
          lastCommand = replyCommand
        } else {
          this.log('You have not yet received a message from another user.')
        }
        break
      case ('help'):
        console.log(fs.readFileSync('./src/instructions.txt').toString())
        break
      case ('encrypt'):
        encryptVal = 0
        if (contents !== 'stop') {
          for (let i = 0; i < contents.length; i++) {
            encryptVal += contents.charCodeAt(i)
          }
        }
        break
      default:
        if (command.charAt(0) === '@') {
          newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (whisper): ${contents}`
          newContents = encrypt(newContents)
          server.write(new Message({ username, command, contents: newContents }).toJSON() + '\n')
          lastCommand = command
          break
        }
        // code to check if there was a previous command if no valid command was given
        if (lastCommand) {
          newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (whisper): ${command} ${contents}`
          newContents = encrypt(newContents)
          server.write(new Message({ username, command: lastCommand, contents: newContents }).toJSON() + '\n')
        } else {
          this.log(`Command <${command}> was not recognized`)
        }
        break
    }
    callback()
  })
