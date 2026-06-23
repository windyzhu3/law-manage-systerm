<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">{{ eyebrow }}</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <div class="heading-actions">
      <slot />
    </div>
  </div>
</template>

<script>
export default {
  name: 'BizPageHeader',
  props: {
    eyebrow: { type: String, default: '' },
    title: { type: String, required: true },
    description: { type: String, default: '' }
  }
}
</script>

<style scoped lang="scss">
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 15px;

  h2 {
    margin: 4px 0;
    color: #172b4d;
    font-size: var(--biz-font-title, 25px);
  }

  p {
    margin: 0;
    color: #7b8ba3;
    font-size: var(--biz-font-base, 13px);
  }
}

.eyebrow {
  color: #2874f0;
  font-size: var(--biz-font-mini, 11px);
  font-weight: 700;
  letter-spacing: 1.5px;
}

.heading-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 760px) {
  .page-heading {
    display: block;
  }

  .heading-actions {
    margin-top: 10px;
    flex-wrap: wrap;
  }
}
</style>
