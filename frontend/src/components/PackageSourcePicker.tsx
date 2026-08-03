import { type ChangeEvent, type DragEvent, useEffect, useRef, useState } from 'react'
import type { SkillUploadInput } from '../lib/skillPackageBuilder'
import { cx } from './classNames'

export interface PackageSourcePickerProps {
  /** 三选一,原始浏览器文件对象一并交给调用方——归一(打包成 zip)与校验不在本组件做。 */
  readonly onSelect: (input: SkillUploadInput) => void
  readonly disabled?: boolean
}

const CARD_CLASS =
  'flex cursor-pointer flex-col items-center justify-center gap-1 rounded-card border border-dashed border-gray-300 px-4 py-6 text-center text-sm font-medium text-gray-700 transition-colors hover:border-brand-500 hover:text-brand-600 dark:border-gray-700 dark:text-gray-200'

/**
 * 上传三形态入口(业务建模 5.1):选文件夹 / 拖入 zip / 单个 SKILL.md。
 *
 * 只负责把浏览器原生的文件选择结果转成 `SkillUploadInput` 交给调用方,不做任何解析或
 * 校验——那是 `pages/UploadPage.tsx` 调 `lib/skillPackageBuilder.ts`/`skillPackageValidator.ts`
 * 的活,本组件保持纯粹的"输入采集"职责。
 *
 * 每张卡片的说明文字放在 `<label>` 外面(而不是嵌套在 label 内部),是为了让隐藏 `<input>`
 * 的可访问名称就是卡片主标题本身(如"选择文件夹"),不含副标题噪音——`getByLabelText`
 * 才能按精确文案查到对应输入框。
 */
export function PackageSourcePicker({ onSelect, disabled = false }: PackageSourcePickerProps) {
  const directoryInputRef = useRef<HTMLInputElement>(null)
  const [dragActive, setDragActive] = useState(false)

  useEffect(() => {
    // `webkitdirectory` 不在 React 的 `InputHTMLAttributes` 类型定义里(非标准属性,但
    // Chrome / Edge / Firefox 116+ 都已实现),用 `setAttribute` 而不是扩展全局 JSX 类型——
    // 后者会影响整个项目对 <input> 元素的类型检查,代价远大于收益。
    directoryInputRef.current?.setAttribute('webkitdirectory', 'true')
  }, [])

  function handleDirectoryChange(event: ChangeEvent<HTMLInputElement>) {
    const files = event.target.files
    if (files && files.length > 0) {
      onSelect({ kind: 'directory', files })
    }
    event.target.value = ''
  }

  function handleZipChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) {
      onSelect({ kind: 'zip', file })
    }
    event.target.value = ''
  }

  function handleSkillMdChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) {
      onSelect({ kind: 'singleSkillMd', file })
    }
    event.target.value = ''
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    setDragActive(false)
    if (disabled) {
      return
    }
    const file = event.dataTransfer.files[0]
    if (file) {
      onSelect({ kind: 'zip', file })
    }
  }

  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="flex flex-col gap-1">
        <label
          htmlFor="upload-directory-input"
          className={cx(CARD_CLASS, disabled && 'pointer-events-none opacity-50')}
        >
          选择文件夹
        </label>
        <p className="text-center text-xs text-gray-400">整个 skill 目录,含 SKILL.md</p>
        <input
          ref={directoryInputRef}
          id="upload-directory-input"
          type="file"
          multiple
          disabled={disabled}
          className="sr-only"
          onChange={handleDirectoryChange}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="upload-zip-input"
          onDragOver={(event) => {
            event.preventDefault()
            if (!disabled) {
              setDragActive(true)
            }
          }}
          onDragLeave={() => setDragActive(false)}
          onDrop={handleDrop}
          className={cx(
            CARD_CLASS,
            dragActive && 'border-brand-500 bg-brand-50 dark:bg-brand-700/10',
            disabled && 'pointer-events-none opacity-50',
          )}
        >
          拖入 zip,或点击选择
        </label>
        <p className="text-center text-xs text-gray-400">已经打好包的 zip 文件</p>
        <input
          id="upload-zip-input"
          type="file"
          accept=".zip"
          disabled={disabled}
          className="sr-only"
          onChange={handleZipChange}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="upload-skill-md-input"
          className={cx(CARD_CLASS, disabled && 'pointer-events-none opacity-50')}
        >
          选择单个 SKILL.md
        </label>
        <p className="text-center text-xs text-gray-400">
          只有一个文件,不含 references/scripts/assets
        </p>
        <input
          id="upload-skill-md-input"
          type="file"
          accept=".md"
          disabled={disabled}
          className="sr-only"
          onChange={handleSkillMdChange}
        />
      </div>
    </div>
  )
}
