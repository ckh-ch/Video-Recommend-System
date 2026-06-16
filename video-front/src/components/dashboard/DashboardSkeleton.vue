<template>
  <div class="skeleton-dashboard">
    <!-- KPI 骨架 -->
    <div class="sk-row kpi-row">
      <div v-for="i in 4" :key="'kpi-'+i" class="sk-card">
        <div class="sk-icon-pulse"></div>
        <div class="sk-lines">
          <div class="sk-line w-24"></div>
          <div class="sk-line w-16"></div>
        </div>
      </div>
    </div>

    <!-- 3-column 图表骨架 -->
    <div class="sk-row sk-3c">
      <div v-for="i in 3" :key="'c3-'+i" class="sk-chart">
        <div class="sk-line w-20"></div>
        <div class="sk-circle"></div>
      </div>
    </div>

    <!-- 全宽图表骨架 -->
    <div class="sk-row">
      <div class="sk-chart sk-bar">
        <div class="sk-line w-28"></div>
        <div class="sk-bar-group">
          <div v-for="i in 8" :key="'bar-'+i" class="sk-bar-item" :style="{ height: heights[i-1] + '%' }"></div>
        </div>
      </div>
    </div>

    <!-- 2-column 骨架 -->
    <div class="sk-row sk-2c">
      <div class="sk-chart">
        <div class="sk-line w-24"></div>
        <div class="sk-tags">
          <div v-for="i in 8" :key="'tag-'+i" class="sk-tag" :style="{ width: tagWidths[i-1] }"></div>
        </div>
      </div>
      <div class="sk-chart">
        <div class="sk-line w-20"></div>
        <div class="sk-cards-row">
          <div v-for="i in 4" :key="'vid-'+i" class="sk-video-card">
            <div class="sk-icon-lg"></div>
            <div class="sk-line w-16"></div>
            <div class="sk-line w-24"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3-column 图表骨架 -->
    <div class="sk-row sk-3c">
      <div v-for="i in 3" :key="'c3b-'+i" class="sk-chart">
        <div class="sk-line w-20"></div>
        <div class="sk-circle"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
const heights = [35, 60, 45, 80, 55, 70, 30, 65]
const tagWidths = ['60px', '45px', '75px', '50px', '65px', '40px', '55px', '70px']
</script>

<style scoped>
.skeleton-dashboard { display: flex; flex-direction: column; gap: 14px; animation: sk-fade-in .3s ease; }
@keyframes sk-fade-in { from { opacity: 0; } to { opacity: 1; } }

.sk-row { display: flex; gap: 14px; }
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.sk-3c { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14px; }
.sk-2c { display: grid; grid-template-columns: 1fr 2fr; gap: 14px; }

/* skeleton card base */
.sk-card {
  background: rgba(15,23,42,.6); border: 1px solid rgba(59,130,246,.08); border-radius: 12px;
  padding: 20px; display: flex; align-items: center; gap: 16px;
}
.sk-chart {
  background: rgba(15,23,42,.6); border: 1px solid rgba(59,130,246,.08); border-radius: 12px;
  padding: 16px; display: flex; flex-direction: column; gap: 12px;
}
.sk-bar { gap: 8px; }

/* shimmer lines */
.sk-lines { display: flex; flex-direction: column; gap: 6px; flex: 1; }
.sk-line {
  height: 12px; border-radius: 6px; background: linear-gradient(90deg, rgba(59,130,246,.08) 25%, rgba(59,130,246,.18) 50%, rgba(59,130,246,.08) 75%);
  background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite;
}
.w-24 { width: 60%; }
.w-20 { width: 50%; }
.w-28 { width: 70%; }
.w-16 { width: 40%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* icon pulse */
.sk-icon-pulse {
  width: 48px; height: 48px; border-radius: 12px;
  background: linear-gradient(90deg, rgba(59,130,246,.1) 25%, rgba(59,130,246,.2) 50%, rgba(59,130,246,.1) 75%);
  background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite; flex-shrink: 0;
}

/* circle for charts */
.sk-circle {
  width: 120px; height: 120px; border-radius: 50%; margin: 0 auto;
  background: linear-gradient(90deg, rgba(59,130,246,.06) 25%, rgba(59,130,246,.14) 50%, rgba(59,130,246,.06) 75%);
  background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite;
}

/* bar chart skeleton */
.sk-bar-group {
  display: flex; align-items: flex-end; gap: 8px; height: 140px; padding: 8px 0;
}
.sk-bar-item {
  flex: 1; border-radius: 4px 4px 0 0;
  background: linear-gradient(90deg, rgba(59,130,246,.1) 25%, rgba(59,130,246,.2) 50%, rgba(59,130,246,.1) 75%);
  background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite;
}

/* tags skeleton */
.sk-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.sk-tag {
  height: 22px; border-radius: 11px;
  background: linear-gradient(90deg, rgba(59,130,246,.08) 25%, rgba(59,130,246,.16) 50%, rgba(59,130,246,.08) 75%);
  background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite;
}

/* video cards skeleton */
.sk-cards-row { display: flex; gap: 12px; overflow: hidden; }
.sk-video-card {
  min-width: 155px; max-width: 155px; border-radius: 10px; padding: 14px 12px;
  background: rgba(30,41,59,.4); display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.sk-icon-lg {
  width: 32px; height: 32px; border-radius: 8px;
  background: linear-gradient(90deg, rgba(59,130,246,.1) 25%, rgba(59,130,246,.2) 50%, rgba(59,130,246,.1) 75%);
  background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite;
}
.sk-video-card .sk-line { animation-delay: 0.2s; }
</style>
