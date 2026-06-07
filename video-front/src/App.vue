<template>
  <div id="app">
    <nav class="nav-bar">
      <router-link to="/recommend/85500">推荐</router-link>
      <router-link to="/hot">热门</router-link>
      <router-link to="/profile/85500">我的画像</router-link>
    </nav>
    <div class="container">
      <router-view />
    </div>
  </div>
</template>

<style>
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }
.nav-bar { background: #fb7299; padding: 0 20px; display: flex; align-items: center; height: 48px; gap: 24px; }
.nav-bar a { color: white; text-decoration: none; font-size: 15px; opacity: 0.9; }
.nav-bar a:hover { opacity: 1; }
.container { max-width: 1200px; margin: 0 auto; padding: 20px; }
</style>
