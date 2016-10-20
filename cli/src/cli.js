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

/**
  *My own super secret highly advanced encryption algorithms, haha nah pretty basic encryption
  *note if encryption is not enabled the message is not actually encrypted but all messages are still
  *at least passed through this function
  *@param a string to be encrypted
  *@return the encrypted sting if encryption is enabled or the original string if not
  */
function encrypt (someString) {
  if (encryptVal) {
    return someString.split('').map(char => char.charCodeAt(0) * encryptVal).join(' ')
  }
  return someString
}

// attempts to process a command with the given contents, if it succeeds it will return true and if not it will return false
function processCommand (command, contents) {
  let newContents = ''
  if (command === 'echo' || command === 'broadcast' || command.charAt(0) === '@') {
    let commandTag = (command.charAt(0) === '@') ? 'whisper' : (command === 'broadcast') ? 'all' : 'echo'
    newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (${commandTag}): ${contents}`
    newContents = encrypt(newContents)
    server.write(new Message({ username, command, contents: newContents }).toJSON() + '\n')
    lastCommand = command
  } else if (command === '/r') {
    if (replyCommand) {
      newContents = dateFormat(new Date(), 'ddd mmm d H:MM:ss Z yyyy') + ` <${username}> (whisper): ${contents}`
      newContents = encrypt(newContents)
      server.write(new Message({ username, command: replyCommand, contents: newContents }).toJSON() + '\n')
      lastCommand = replyCommand
    } else {
      this.log('You have not yet received a message from another user.')
    }
  } else if (command === 'users') {
    server.write(new Message({ username, command, contents: newContents }).toJSON() + '\n')
    lastCommand = command
  } else if (command === 'encrypt') {
    encryptVal = 0
    if (contents !== 'stop') {
      for (let i = 0; i < contents.length; i++) {
        encryptVal += contents.charCodeAt(i)
      }
    }
  } else if (command === 'help') {
    this.log(fs.readFileSync('./src/instructions.txt').toString())
  } else if (command === 'disconnect') {
    server.end(new Message({ username, command }).toJSON() + '\n')
  } else {
    return false
  }
  return true
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
        if (command === 'echo' || command === 'broadcast' || command.charAt(0) === '@') {
          // my basic decription algorithm
          let encryptedContentsArr = contents.split(' ')
          let unencryptedContentsArr = encryptedContentsArr.map(encChar => String.fromCharCode(encChar / encryptVal))
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

    if (!processCommand(command, contents)) {
      if (!lastCommand) {
        this.log(`Command <${command}> was not recognized`)
        this.log(fs.readFileSync('./src/instructions.txt').toString())
      } else if (!processCommand(lastCommand, command + ' ' + contents)) {
        this.log(`you broke something because last command should be false unless a valid command took place, so you should never be here`)
      }
    }
    callback()
  })
