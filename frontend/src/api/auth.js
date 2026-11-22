
import client from './client'

export function register({
  username,
  email,
  password,
  fullName,
  phoneNumber,
  organization,
  accessReason,
}) {
  return client.post('/auth/register', {
    username,
    email,
    password,
    fullName,
    phoneNumber,
    organization,
    accessReason,
  })
}

export function login({ usernameOrEmail, password }) {
  return client.post('/auth/login', {
    usernameOrEmail,
    password,
  })
}
