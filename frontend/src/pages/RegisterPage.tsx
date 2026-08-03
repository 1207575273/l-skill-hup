import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { TextInput } from '../components/TextInput'
import { useRegister } from '../hooks/useAuthMutations'
import { useAuthProviders } from '../hooks/useAuthQueries'

/**
 * 本地注册页(建模文档 3.2)。
 *
 * <p><b>开关由服务端说了算,前端不硬编码。</b> `localRegisterEnabled` 为 false 时直接告诉用户"注册已关闭",
 * 而不是渲染一个提交必然失败的表单 —— 这和登录页决定要不要显示注册入口是同一条判据,两处必须一致:
 * 入口藏起来但页面能直接访问,等于留了一扇没人看守的门。
 *
 * <p><b>确认密码只在前端比对,不发给服务端。</b> 它是防手滑的输入辅助,不是业务字段;
 * 发过去只会让契约多一个服务端必须重复校验、却没有任何业务含义的字段。
 */
export default function RegisterPage() {
  const navigate = useNavigate()
  const providersQuery = useAuthProviders()
  const registerMutation = useRegister()

  const [displayName, setDisplayName] = useState('')
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  // 两次密码不一致时**不显示为错误**,只是禁用提交:用户还在输第二遍的过程中,
  // 每敲一个字符就红一次是纯粹的干扰。
  const passwordsMatch = password === confirmPassword
  const canSubmit =
    displayName.trim() !== '' &&
    login.trim() !== '' &&
    password !== '' &&
    passwordsMatch &&
    !registerMutation.isPending

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    // 注册成功即签发会话(与登录同一个响应体),所以直接回首页,不再跳去登录页让人再输一遍。
    registerMutation.mutate({ displayName, login, password }, { onSuccess: () => navigate('/') })
  }

  if (providersQuery.isPending) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" label="正在读取登录方式" />
      </div>
    )
  }

  if (providersQuery.data?.localRegisterEnabled !== true) {
    // 给明确说明而不是重定向回登录页:直接跳走的话,用户会以为是自己点错了链接,
    // 而真实情况是"这个功能被管理员关了",两者该做的下一步完全不同。
    return (
      <EmptyState
        title="注册未开放"
        description="当前未开放自助注册,请联系管理员创建账号。"
        action={
          <Link to="/login" className="text-brand-600 hover:underline dark:text-brand-500">
            返回登录
          </Link>
        }
      />
    )
  }

  return (
    <div className="mx-auto flex max-w-sm flex-col gap-6">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">注册</h1>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <TextInput
          label="显示名"
          autoComplete="nickname"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
        />
        <TextInput
          label="登录名"
          autoComplete="username"
          value={login}
          onChange={(event) => setLogin(event.target.value)}
        />
        <TextInput
          label="密码"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <TextInput
          label="确认密码"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
        />

        {/* 只在用户确实输了第二遍之后才提示,避免边输边红 */}
        {confirmPassword !== '' && !passwordsMatch && (
          <p className="text-sm text-danger-600 dark:text-danger-400">两次输入的密码不一致</p>
        )}

        {registerMutation.isError && <ErrorBanner error={registerMutation.error as ApiError} />}

        <Button type="submit" fullWidth loading={registerMutation.isPending} disabled={!canSubmit}>
          注册
        </Button>
      </form>

      <p className="text-center text-sm text-gray-500 dark:text-gray-400">
        已有账号?{' '}
        <Link to="/login" className="text-brand-600 hover:underline dark:text-brand-500">
          去登录
        </Link>
      </p>
    </div>
  )
}
