import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SkillFileTree } from './SkillFileTree'

function renderTree(
  files: { path: string; sizeBytes: number }[],
  options: { selectedPath?: string | null; onSelectFile?: (path: string) => void } = {},
) {
  return render(
    <SkillFileTree
      files={files}
      selectedPath={options.selectedPath ?? null}
      onSelectFile={options.onSelectFile ?? (() => {})}
    />,
  )
}

describe('SkillFileTree', () => {
  it('should_render_every_file_when_manifest_given', () => {
    renderTree([
      { path: 'SKILL.md', sizeBytes: 1024 },
      { path: 'references/guide.md', sizeBytes: 2048 },
      { path: 'scripts/run.sh', sizeBytes: 512 },
    ])

    expect(screen.getByText('SKILL.md')).toBeInTheDocument()
    expect(screen.getByText('guide.md')).toBeInTheDocument()
    expect(screen.getByText('run.sh')).toBeInTheDocument()
  })

  it('should_show_directories_as_nested_lists_when_manifest_has_subdirectories', () => {
    renderTree([
      { path: 'references/a.md', sizeBytes: 1 },
      { path: 'references/deep/b.md', sizeBytes: 2 },
    ])

    expect(screen.getByText('references')).toBeInTheDocument()
    expect(screen.getByText('deep')).toBeInTheDocument()
  })

  it('should_render_all_nodes_expanded_without_any_collapse_toggle', () => {
    // 不做折叠:这个部件的目的就是一眼看清 skill 的全部形态。深层文件必须直接可见。
    renderTree([{ path: 'a/b/c/deep.md', sizeBytes: 1 }])

    expect(screen.getByText('deep.md')).toBeInTheDocument()
  })

  it('should_call_select_with_full_path_when_file_clicked', () => {
    // 回调必须给**完整路径**而不是文件名 —— 后端按 path 精确匹配 zip 条目,
    // 给个 `guide.md` 它找不到,而两个目录下同名文件也会撞车。
    const onSelectFile = vi.fn()
    renderTree([{ path: 'references/guide.md', sizeBytes: 1 }], { onSelectFile })

    fireEvent.click(screen.getByRole('button', { name: /guide\.md/ }))

    expect(onSelectFile).toHaveBeenCalledWith('references/guide.md')
  })

  it('should_not_make_directories_clickable', () => {
    // 目录没有内容可预览,让它可点只会制造一次无效点击。
    renderTree([{ path: 'references/guide.md', sizeBytes: 1 }])

    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0]).toHaveAccessibleName(/guide\.md/)
  })

  it('should_mark_selected_file_as_current', () => {
    renderTree(
      [
        { path: 'SKILL.md', sizeBytes: 1 },
        { path: 'README.md', sizeBytes: 2 },
      ],
      { selectedPath: 'SKILL.md' },
    )

    expect(screen.getByRole('button', { name: /SKILL\.md/ })).toHaveAttribute(
      'aria-current',
      'true',
    )
    expect(screen.getByRole('button', { name: /README\.md/ })).not.toHaveAttribute('aria-current')
  })

  it('should_show_file_size_beside_each_file', () => {
    renderTree([{ path: 'SKILL.md', sizeBytes: 2048 }])

    // formatFileSize 对 B 以上的单位固定两位小数,复用它而不是另写一份格式化 ——
    // 详情页别处显示体积也是这个函数,两处格式不一致会让人以为是两个不同的数。
    expect(screen.getByText('2.00 KB')).toBeInTheDocument()
  })

  it('should_show_placeholder_when_manifest_is_empty', () => {
    // 存量版本(在文件清单能力上线之前上传的)拿到的是空数组,
    // 要说明"没有清单"而不是渲染一个空白框让人以为页面坏了。
    renderTree([])

    expect(screen.getByText('该版本暂无文件清单信息')).toBeInTheDocument()
  })
})
