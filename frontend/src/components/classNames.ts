/**
 * 拼接 Tailwind 类名的小工具,过滤掉 false/null/undefined,避免每个组件里各写一遍
 * `.filter(Boolean).join(' ')`。不引 clsx/classnames 这类第三方包(项目宪法:不新增依赖)。
 */
export function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter((value): value is string => Boolean(value)).join(' ')
}
