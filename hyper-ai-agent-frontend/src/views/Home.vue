<template>
  <div class="home-container">
    <div class="header">
      <h1>Hyper AI Agent</h1>
      <p class="subtitle">选择您想要使用的AI应用</p>
    </div>
    
    <div class="apps-grid">
      <div class="app-card" @click="goToPsychology">
        <div class="app-icon psychology-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" fill="currentColor"/>
            <path d="M12.5 11H11c-.55 0-1-.45-1-1s.45-1 1-1h1.5c.83 0 1.5-.67 1.5-1.5S13.33 6 12.5 6H11c-.55 0-1-.45-1-1s.45-1 1-1h1.5c1.93 0 3.5 1.57 3.5 3.5S14.43 12 12.5 12zM7.5 13h2c.55 0 1 .45 1 1s-.45 1-1 1h-2c-.83 0-1.5.67-1.5 1.5S6.67 18 7.5 18h2c.55 0 1 .45 1 1s-.45 1-1 1h-2c-1.93 0-3.5-1.57-3.5-3.5S5.57 13 7.5 13z" fill="currentColor"/>
          </svg>
        </div>
        <h2>AI 心理咨询大师</h2>
        <p>专业的AI心理咨询服务，为您提供心理支持和指导</p>
      </div>
      
      <div class="app-card" @click="goToManus">
        <div class="app-icon manus-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7v10c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-10-5z" fill="currentColor"/>
          </svg>
        </div>
        <h2>AI 超级智能体</h2>
        <p>强大的AI智能助手，帮您解决各种问题</p>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Home',
  methods: {
    goToPsychology() {
      this.$router.push('/psychology')
    },
    goToManus() {
      this.$router.push('/manus')
    }
  }
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.header {
  text-align: center;
  color: white;
  margin-bottom: 60px;
}

.header h1 {
  font-size: 3.5em;
  font-weight: 700;
  margin-bottom: 10px;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}

.subtitle {
  font-size: 1.2em;
  opacity: 0.9;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 30px;
  max-width: 800px;
  width: 100%;
}

.app-card {
  background: white;
  border-radius: 20px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.app-card:hover {
  transform: translateY(-10px);
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.3);
}

.app-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.psychology-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.manus-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.app-icon svg {
  width: 50px;
  height: 50px;
}

.app-card h2 {
  font-size: 1.8em;
  margin-bottom: 15px;
  color: #333;
}

.app-card p {
  color: #666;
  line-height: 1.6;
  font-size: 1em;
}

@media (max-width: 768px) {
  .header h1 {
    font-size: 2.5em;
  }
  
  .apps-grid {
    grid-template-columns: 1fr;
  }
}
</style>
