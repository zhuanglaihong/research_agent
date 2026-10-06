import axios from 'axios'
import { API_BASE_URL } from '@/config/env'

const myAxios = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: false,
})

export default myAxios
