import {
  Atom,
  BookOpen,
  Brain,
  Briefcase,
  Calculator,
  ChartColumn,
  Code,
  FlaskConical,
  Globe,
  Languages,
  Music,
  Palette,
  type LucideIcon,
} from 'lucide-react'
import type { WorkspaceIcon } from '../types'

/** Hình vẽ của từng mã biểu tượng không gian học. */
export const workspaceIcons: Record<WorkspaceIcon, LucideIcon> = {
  BOOK: BookOpen,
  CALCULATOR: Calculator,
  FLASK: FlaskConical,
  ATOM: Atom,
  GLOBE: Globe,
  CODE: Code,
  LANGUAGES: Languages,
  CHART: ChartColumn,
  BRAIN: Brain,
  PALETTE: Palette,
  MUSIC: Music,
  BRIEFCASE: Briefcase,
}
