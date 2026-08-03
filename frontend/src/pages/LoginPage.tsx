import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { API_BASE_URL, type ApiError } from '../api/client'
import { Button } from '../components/Button'
import { ErrorBanner } from '../components/ErrorBanner'
import { TextInput } from '../components/TextInput'
import { useLogin } from '../hooks/useAuthMutations'
import { useAuthProviders } from '../hooks/useAuthQueries'

/**
 * 本地登录页(建模文档 3.2/6.2)。**注册入口与钉钉登录按钮是否显示,完全由
 * `GET /auth/providers` 的响应决定**(`localRegisterEnabled` / `providers`),
 * 不硬编码——"让服务端告诉前端有哪些按钮,而不是前端硬编码一个点了就报错的按钮"。
 *
 * 钉钉登录本身(扫码换 token)是 P1,这里只按 `providers` 是否包含 `DINGTALK`
 * 决定要不要露出入口;注册表单本身也不在本轮范围内(P0 默认关闭)。
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const providersQuery = useAuthProviders()
  const loginMutation = useLogin()

  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')

  const canSubmit = login.trim() !== '' && password !== '' && !loginMutation.isPending

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    loginMutation.mutate({ login, password }, { onSuccess: () => navigate('/') })
  }

  // 数据未回来之前一律按"关闭"处理——P0 的默认值本来就是关闭,这个默认值与
  // 服务端最终返回的一致,不会出现"闪现后又消失"的入口。
  const showRegisterEntry = providersQuery.data?.localRegisterEnabled === true
  const showDingtalkEntry = providersQuery.data?.providers.includes('DINGTALK') ?? false

  return (
    <div className="mx-auto flex max-w-sm flex-col gap-6">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">登录</h1>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <TextInput
          label="登录名"
          autoComplete="username"
          value={login}
          onChange={(event) => setLogin(event.target.value)}
        />
        <TextInput
          label="密码"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {loginMutation.isError && <ErrorBanner error={loginMutation.error as ApiError} />}

        <Button type="submit" fullWidth loading={loginMutation.isPending} disabled={!canSubmit}>
          登录
        </Button>
      </form>

      {showDingtalkEntry && (
        <a
          href={`${API_BASE_URL}/auth/dingtalk/authorize`}
          className="inline-flex h-10 items-center justify-center rounded-control border border-gray-300 text-sm text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
        >
          钉钉扫码登录
        </a>
      )}

      {showRegisterEntry && (
        <p className="text-center text-sm text-gray-500 dark:text-gray-400">
          还没有账号?{' '}
          <Link to="/register" className="text-brand-600 hover:underline dark:text-brand-500">
            注册
          </Link>
        </p>
      )}
    </div>
  )
}
