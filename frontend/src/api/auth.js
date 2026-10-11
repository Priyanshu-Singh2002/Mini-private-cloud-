import client from './client'

export function register({ username, email, password }) {
  return client.post('/auth/register', {
    username,
    email,
    password,
  })
}

export function login({ usernameOrEmail, password }) {
  return client.post('/auth/login', {
    usernameOrEmail,
    password,
  })
}
