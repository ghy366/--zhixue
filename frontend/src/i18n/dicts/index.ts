import { dict as common } from './common'
import { dict as workspace } from './workspace'
import { dict as auth } from './auth'
import { dict as misc } from './misc'
import { dict as chat } from './chat'
import { dict as adminB } from './admin_b'
import { dict as adminA } from './admin_a'
import { dict as teacher } from './teacher'
import { dict as studentC } from './student_c'
import { dict as studentB } from './student_b'
import { dict as studentA } from './student_a'

/** 全部英文字典合并（key = 中文原文） */
export const en: Record<string, string> = {
  ...common,
  ...workspace,
  ...auth,
  ...misc,
  ...chat,
  ...adminB,
  ...adminA,
  ...teacher,
  ...studentC,
  ...studentB,
  ...studentA,
}
