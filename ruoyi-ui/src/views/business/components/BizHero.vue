<template>
  <section class="biz-hero">
    <div>
      <span>{{ eyebrow }}</span>
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </div>
    <div class="hero-rings"><i /><i /><i /></div>
  </section>
</template>

<script>
export default {
  name: 'BizHero',
  props: {
    eyebrow: { type: String, default: '' },
    title: { type: String, required: true },
    description: { type: String, default: '' }
  }
}
</script>

<style scoped lang="scss">
.biz-hero {
  position: relative;
  min-height: 154px;
  overflow: hidden;
  border-radius: 13px;
  background: linear-gradient(115deg, #123ee9 0%, #086ce8 55%, #8237ed 100%);
  box-shadow: 0 12px 24px rgba(25, 74, 221, .18);
  color: #fff;

  > div:first-child {
    position: relative;
    z-index: 2;
    padding: 24px 28px;
  }

  span {
    color: #b8d8ff;
    font-size: var(--biz-font-mini, 11px);
    font-weight: 700;
    letter-spacing: 1.6px;
  }

  h3 {
    margin: 6px 0;
    font-size: var(--biz-font-hero, 27px);
  }

  p {
    margin: 0;
    color: #d9e8ff;
    font-size: var(--biz-font-base, 13px);
  }
}

.hero-rings {
  position: absolute;
  right: 0;
  top: 0;
  width: 38%;
  height: 100%;
  opacity: .65;

  i {
    position: absolute;
    border: 1px solid rgba(255, 255, 255, .25);
    border-radius: 50%;
    background: rgba(255, 255, 255, .08);
  }

  i:nth-child(1) {
    right: 30px;
    top: 18px;
    width: 110px;
    height: 110px;
  }

  i:nth-child(2) {
    right: 120px;
    bottom: -30px;
    width: 160px;
    height: 160px;
  }

  i:nth-child(3) {
    right: -60px;
    bottom: -70px;
    width: 230px;
    height: 230px;
  }
}

@media (max-width: 760px) {
  .hero-rings {
    display: none;
  }
}
</style>
