<template>
  <nav ref="rail" class="journey-step-nav" aria-label="待办模板配置步骤">
    <button
      v-for="(step, index) in steps"
      :key="step.code"
      :ref="`step-${step.code}`"
      type="button"
      class="journey-step-nav__item"
      :class="[`is-${stateOf(step).toLowerCase()}`, { 'is-active': step.code === activeCode }]"
      :aria-current="step.code === activeCode ? 'step' : null"
      @click="$emit('select', step.code)"
    >
      <span class="journey-step-nav__marker" aria-hidden="true">
        <i v-if="stateOf(step) === 'COMPLETED'" class="el-icon-check" />
        <i v-else-if="stateOf(step) === 'WARNING'" class="el-icon-warning-outline" />
        <i v-else-if="stateOf(step) === 'BLOCKED'" class="el-icon-close" />
        <span v-else>{{ index + 1 }}</span>
      </span>
      <span class="journey-step-nav__copy">
        <strong>{{ fallbackTitle(step.code) || step.title }}</strong>
        <small>{{ stateLabel(stateOf(step)) }}</small>
      </span>
    </button>
  </nav>
</template>

<script>
const TITLES = {
  EVENT: '业务事件',
  TRIGGER: '触发条件',
  OWNER: '负责人',
  DOD: '完成标准',
  SLA: '办理时限',
  ROUTING: '后续路由',
  SIMULATION_PUBLISH: '模拟发布'
}

export default {
  name: 'JourneyStepNav',
  props: {
    steps: { type: Array, default: () => [] },
    activeCode: { type: String, default: '' }
  },
  mounted() {
    this.ensureActiveVisible()
  },
  watch: {
    activeCode() {
      this.ensureActiveVisible()
    }
  },
  methods: {
    ensureActiveVisible() {
      this.$nextTick(() => {
        const reference = this.$refs[`step-${this.activeCode}`]
        const target = Array.isArray(reference) ? reference[0] : reference
        if (!target || typeof target.scrollIntoView !== 'function') return
        const reduced = typeof window !== 'undefined' &&
          window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
        target.scrollIntoView({
          behavior: reduced ? 'auto' : 'smooth',
          block: 'nearest',
          inline: 'center'
        })
      })
    },
    stateOf(step) {
      const state = String((step && step.state) || 'NOT_STARTED').toUpperCase()
      if (state === 'COMPLETED') return 'COMPLETED'
      if (state === 'WARNING') return 'WARNING'
      if (state === 'BLOCKED') return 'BLOCKED'
      if (state === 'IN_PROGRESS') return 'IN_PROGRESS'
      return 'NOT_STARTED'
    },
    stateLabel(state) {
      return {
        NOT_STARTED: '未开始',
        IN_PROGRESS: '配置中',
        COMPLETED: '已完成',
        WARNING: '需检查',
        BLOCKED: '被阻塞'
      }[state] || '未开始'
    },
    fallbackTitle(code) {
      return TITLES[code] || '配置步骤'
    }
  }
}
</script>

<style scoped lang="scss">
.journey-step-nav {
  display: grid;
  grid-template-columns: repeat(7, minmax(112px, 1fr));
  gap: 0;
  padding: 20px 24px;
  overflow-x: auto;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;
}

.journey-step-nav__item {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 112px;
  padding: 0 10px;
  color: #65758A;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;

  &::after {
    position: absolute;
    top: 17px;
    right: -14px;
    z-index: 0;
    width: 28px;
    height: 2px;
    content: "";
    background: #D9E1EA;
  }

  &:last-child::after {
    display: none;
  }

  &:focus-visible {
    outline: 2px solid #C89A3D;
    outline-offset: 3px;
  }

  &.is-active {
    color: #0B2A55;
  }
}

.journey-step-nav__marker {
  position: relative;
  z-index: 1;
  display: inline-flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-right: 10px;
  font-weight: 700;
  color: #65758A;
  background: #F4F7FA;
  border: 2px solid #D9E1EA;
  border-radius: 50%;
}

.is-active .journey-step-nav__marker {
  color: #FFFFFF;
  background: #C89A3D;
  border-color: #C89A3D;
}

.is-completed .journey-step-nav__marker {
  color: #FFFFFF;
  background: #0B2A55;
  border-color: #0B2A55;
}

.is-warning .journey-step-nav__marker {
  color: #7A5210;
  background: #FFF6E4;
  border-color: #C89A3D;
}

.is-blocked .journey-step-nav__marker {
  color: #FFFFFF;
  background: #C43D3D;
  border-color: #C43D3D;
}

.journey-step-nav__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;

  strong {
    overflow: hidden;
    font-size: 14px;
    line-height: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    font-size: 12px;
    line-height: 18px;
    color: #8996A7;
  }
}

@media (max-width: 960px) {
  .journey-step-nav {
    grid-template-columns: repeat(7, minmax(124px, 1fr));
  }
}

@media (max-width: 640px) {
  .journey-step-nav {
    display: flex;
    gap: 0;
    padding: 16px;
    overflow-x: auto;
    scroll-padding-inline: 16px;
    scroll-snap-type: x proximity;
    scrollbar-width: thin;
  }

  .journey-step-nav__item {
    flex: 0 0 144px;
    min-width: 144px;
    padding: 6px;
    scroll-snap-align: center;

    &::after {
      display: none;
    }
  }
}
</style>
