/**
 * 订单状态元数据：文案 + 图表色 + 语义色档。
 *
 * 口径与后端 OrderStatus 枚举一致：1 待支付 / 2 已支付 / 3 已发货 / 4 已完成 / 5 已取消。
 *
 * 工作台的状态分布图与订单页的汇总胶囊共用这一份 —— 改状态色只需要改这里。
 * 此前两个页面各抄一份，而工作台的注释还写着「颜色只在这里定义一份」，注释与事实不符；
 * 那种情况下改主题色必然漏掉一处，且不会有任何报错。
 */
export const STATUS_META = [
  { status: 1, text: '待支付', color: '#E09520', tone: 'warn' },
  { status: 2, text: '已支付', color: '#6C4DF2', tone: 'brand' },
  { status: 3, text: '已发货', color: '#3E7BDB', tone: 'info' },
  { status: 4, text: '已完成', color: '#1F9D63', tone: 'ok' },
  { status: 5, text: '已取消', color: '#B4BAC6', tone: 'gray' }
]
