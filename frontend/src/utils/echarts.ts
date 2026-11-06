/**
 * ECharts 按需注册 + 统一主题参数
 * 只注册分析中心用到的图表/组件，控制包体；进分析中心路由才加载。
 */
import * as echarts from 'echarts/core'
import {
  LineChart, BarChart, ScatterChart, BoxplotChart, SankeyChart,
  FunnelChart, HeatmapChart, RadarChart, PieChart, CustomChart,
} from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  MarkLineComponent, MarkPointComponent, VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart, BarChart, ScatterChart, BoxplotChart, SankeyChart,
  FunnelChart, HeatmapChart, RadarChart, PieChart, CustomChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  MarkLineComponent, MarkPointComponent, VisualMapComponent,
  CanvasRenderer,
])

export default echarts

/** 双主题都清晰的公共配色 */
export const PALETTE = {
  indigo: '#2bb187', cyan: '#22d3ee', green: '#34d399', amber: '#f59e0b',
  rose: '#fb7185', purple: '#38bdf8', blue: '#38bdf8', grey: '#94a3b8',
  text: '#94a3b8', split: 'rgba(148,163,184,.16)', axis: 'rgba(148,163,184,.35)',
}

/** 通用 tooltip 皮肤 */
export const TOOLTIP = {
  backgroundColor: 'rgba(15,23,42,.92)',
  borderColor: 'rgba(148,163,184,.25)',
  borderWidth: 1,
  textStyle: { color: '#f1f5f9', fontSize: 12 },
  extraCssText: 'backdrop-filter: blur(6px); border-radius: 10px; box-shadow: 0 8px 24px rgba(0,0,0,.25);',
}

/** 直角坐标系公共轴样式 */
export function catAxis(data: string[] | number[]) {
  return {
    type: 'category' as const,
    data,
    axisLine: { lineStyle: { color: PALETTE.axis } },
    axisTick: { show: false },
    axisLabel: { color: PALETTE.text, fontSize: 11 },
  }
}
export function valAxis(name?: string) {
  return {
    type: 'value' as const,
    name,
    nameTextStyle: { color: PALETTE.text, fontSize: 10 },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: PALETTE.split } },
    axisLabel: { color: PALETTE.text, fontSize: 11 },
  }
}
