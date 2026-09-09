class SmokerController {
    constructor() {
        this.ws = null;
        this.uplot = null;
        this.chartData = [[], [], [], [], []]; // time, temp_chamber, temp_target, temp_product1, temp_product2
        this.commandQueue = [];
        this.reconnectAttempts = 0;
        this.maxReconnectDelay = 30000;
        this.lastUpdate = Date.now();
        this.currentProgram = null;
        this.currentStage = 1;
        
        this.init();
    }
    
    async init() {
        this.registerServiceWorker();
        this.initChart();
        this.bindEvents();
        this.updateTime();
        this.connectWebSocket();
        
        // Обновление времени каждую секунду
        setInterval(() => this.updateTime(), 1000);
        
        // Пинг каждые 30 секунд
        setInterval(() => this.sendPing(), 30000);
    }
    
    registerServiceWorker() {
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.register('/sw.js')
                .then(reg => {
                    console.log('Service Worker зарегистрирован');
                    
                    // Проверка обновлений
                    reg.addEventListener('updatefound', () => {
                        const newWorker = reg.installing;
                        newWorker.addEventListener('statechange', () => {
                            if (newWorker.state === 'installed') {
                                if (navigator.serviceWorker.controller) {
                                    this.showUpdateNotification();
                                }
                            }
                        });
                    });
                })
                .catch(err => console.error('Service Worker ошибка:', err));
        }
    }
    
    initChart() {
        const opts = {
            width: document.getElementById('process-chart').offsetWidth,
            height: 300,
            series: [
                { label: "Время" },
                {
                    label: "T° Камеры",
                    stroke: "#e74c3c",
                    width: 2,
                    fill: "rgba(231, 76, 60, 0.1)"
                },
                {
                    label: "Цель",
                    stroke: "#f39c12",
                    width: 1,
                    dash: [5, 5]
                },
                {
                    label: "T° Продукта 1",
                    stroke: "#3498db",
                    width: 2
                },
                {
                    label: "T° Продукта 2",
                    stroke: "#9b59b6",
                    width: 2
                }
            ],
            scales: {
                x: { time: true },
                y: {
                    range: (self, min, max) => [Math.min(min, 0), Math.max(max, 120)]
                }
            },
            legend: {
                show: false
            },
            cursor: {
                show: true,
                x: true,
                y: true
            }
        };
        
        this.uplot = new uPlot(opts, this.chartData, document.getElementById('process-chart'));
        
        // Обработчики масштабирования
        document.getElementById('zoom-in').addEventListener('click', () => this.zoomChart(0.8));
        document.getElementById('zoom-out').addEventListener('click', () => this.zoomChart(1.2));
        document.getElementById('reset-zoom').addEventListener('click', () => this.resetZoom());
    }
    
    bindEvents() {
        // Навигация
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const section = item.dataset.section;
                this.showSection(section);
            });
        });
        
        // Управление процессом
        document.getElementById('start-btn').addEventListener('click', () => this.sendCommand('start'));
        document.getElementById('pause-btn').addEventListener('click', () => this.sendCommand('pause'));
        document.getElementById('stop-btn').addEventListener('click', () => this.sendCommand('stop'));
        
        // Управление графиком
        window.addEventListener('resize', () => this.resizeChart());
    }
    
    showSection(sectionId) {
        // Скрыть все секции
        document.querySelectorAll('.section').forEach(section => {
            section.classList.remove('active');
        });
        
        // Убрать активный класс со всех пунктов меню
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
        });
        
        // Показать выбранную секцию
        const activeSection = document.getElementById(sectionId);
        if (activeSection) {
            activeSection.classList.add('active');
            
            // Активировать пункт меню
            const activeNavItem = document.querySelector(`.nav-item[data-section="${sectionId}"]`);
            if (activeNavItem) {
                activeNavItem.classList.add('active');
            }
            
            // Обновить график при показе мониторинга
            if (sectionId === 'monitoring' && this.uplot) {
                setTimeout(() => this.resizeChart(), 100);
            }
        }
    }
    
    resizeChart() {
        if (this.uplot) {
            const container = document.getElementById('process-chart');
            this.uplot.setSize({
                width: container.offsetWidth,
                height: 300
            });
        }
    }
    
    zoomChart(factor) {
        if (this.uplot) {
            const xRange = this.uplot.scales.x.max - this.uplot.scales.x.min;
            const center = (this.uplot.scales.x.max + this.uplot.scales.x.min) / 2;
            const newRange = xRange * factor;
            
            this.uplot.setScale('x', {
                min: center - newRange / 2,
                max: center + newRange / 2
            });
        }
    }
    
    resetZoom() {
        if (this.uplot && this.chartData[0].length > 0) {
            const minTime = this.chartData[0][0];
            const maxTime = this.chartData[0][this.chartData[0].length - 1];
            
            this.uplot.setScale('x', {
                min: minTime,
                max: maxTime
            });
        }
    }
    
    updateTime() {
        const now = new Date();
        const timeStr = now.toLocaleTimeString('ru-RU');
        document.querySelector('#current-time span').textContent = timeStr;
        
        // Обновление таймера процесса
        if (document.getElementById('process-status').classList.contains('running')) {
            this.updateProcessTimer();
        }
    }
    
    updateProcessTimer() {
        const timerElement = document.getElementById('process-timer span');
        // Логика обновления таймера (будет интегрирована с бэкендом)
    }
    
    connectWebSocket() {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            return;
        }
        
        const wsUrl = `ws://${window.location.host}/ws`;
        this.ws = new WebSocket(wsUrl);
        
        this.ws.onopen = () => {
            console.log('WebSocket подключен');
            this.reconnectAttempts = 0;
            this.updateConnectionStatus('connected');
            
            // Отправить команды из очереди
            this.flushCommandQueue();
        };
        
        this.ws.onmessage = (event) => {
            this.lastUpdate = Date.now();
            
            try {
                const data = JSON.parse(event.data);
                this.handleMessage(data);
            } catch (error) {
                console.error('Ошибка парсинга WebSocket сообщения:', error);
            }
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket ошибка:', error);
            this.updateConnectionStatus('error');
        };
        
        this.ws.onclose = () => {
            console.log('WebSocket отключен');
            this.updateConnectionStatus('disconnected');
            
            // Попытка переподключения
            this.reconnect();
        };
    }
    
    reconnect() {
        if (this.reconnectAttempts > 10) {
            console.log('Превышено максимальное количество попыток переподключения');
            return;
        }
        
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), this.maxReconnectDelay);
        
        console.log(`Попытка переподключения через ${delay}мс`);
        
        setTimeout(() => {
            this.reconnectAttempts++;
            this.connectWebSocket();
        }, delay);
    }
    
    handleMessage(data) {
        // Обновление статуса
        if (data.status) {
            this.updateStatus(data.status);
        }
        
        // Обновление графика
        if (data.temperatures) {
            this.updateChart(data.temperatures);
        }
        
        // Обновление программы
        if (data.program) {
            this.updateProgram(data.program);
        }
        
        // Обновление журнала
        if (data.log) {
            this.updateLog(data.log);
        }
        
        // Ответ на команду
        if (data.ack) {
            console.log(`Команда выполнена: ${data.ack}`);
        }
        
        // Ответ на пинг
        if (data.type === 'pong') {
            console.log('Получен pong');
        }
    }
    
    updateStatus(status) {
        // Температуры
        if (status.T_air !== undefined) {
            this.updateElement('#temp-chamber', `${status.T_air.toFixed(1)} °C`);
            this.updateProgress('#chamber-progress', status.T_air, status.target_T_air || 100);
        }
        
        if (status.T1 !== undefined) {
            this.updateElement('#temp-product1', `${status.T1.toFixed(1)} °C`);
            this.updateProgress('#product1-progress', status.T1, status.target_T1 || 100);
        }
        
        if (status.T2 !== undefined) {
            // Обновить продукт 2 если есть
        }
        
        // Статус процесса
        if (status.state) {
            this.updateProcessStatus(status.state);
        }
        
        // Исполнительные устройства
        this.updateDevice('heater', status.heater, status.heater_pwm);
        this.updateDevice('fan', status.fan, status.fan_pwm);
        this.updateDevice('smoke_damper', status.smoke_damper);
        this.updateDevice('vent_damper', status.vent_damper);
        
        // Целевые температуры
        if (status.target_T_air !== undefined) {
            this.updateElement('#temp-chamber-target', `${status.target_T_air.toFixed(1)} °C`);
        }
        
        if (status.target_T1 !== undefined) {
            this.updateElement('#temp-product1-target', `${status.target_T1.toFixed(1)} °C`);
        }
        
        // Wi-Fi RSSI
        if (status.wifi_rssi !== undefined) {
            this.updateWifiStatus(status.wifi_rssi);
        }
    }
    
    updateProcessStatus(state) {
        const statusElement = document.getElementById('process-status');
        const startBtn = document.getElementById('start-btn');
        const pauseBtn = document.getElementById('pause-btn');
        const stopBtn = document.getElementById('stop-btn');
        
        // Обновить классы и текст
        statusElement.className = 'status-value ' + state.toLowerCase();
        statusElement.textContent = this.getStateText(state);
        
        // Обновить состояние кнопок
        switch (state) {
            case 'IDLE':
                startBtn.disabled = false;
                pauseBtn.disabled = true;
                stopBtn.disabled = true;
                break;
            case 'RUNNING':
                startBtn.disabled = true;
                pauseBtn.disabled = false;
                stopBtn.disabled = false;
                break;
            case 'PAUSED':
                startBtn.disabled = true;
                pauseBtn.disabled = true;
                stopBtn.disabled = false;
                break;
        }
    }
    
    getStateText(state) {
        const states = {
            'IDLE': 'ОЖИДАНИЕ',
            'RUNNING': 'ВЫПОЛНЕНИЕ',
            'PAUSED': 'ПАУЗА',
            'ERROR': 'ОШИБКА'
        };
        return states[state] || state;
    }
    
    updateDevice(device, isActive, pwm = null) {
        const deviceElement = document.querySelector(`[data-device="${device}"]`);
        if (!deviceElement) return;
        
        const statusElement = deviceElement.querySelector('.device-status');
        const detailsElement = deviceElement.querySelector('.device-details');
        
        if (pwm !== null && pwm > 0) {
            // ШИМ режим
            statusElement.textContent = `ШИМ ${pwm}%`;
            statusElement.className = 'device-status pwm';
            if (detailsElement) {
                detailsElement.textContent = `${pwm}% мощности`;
            }
        } else if (isActive) {
            // Включено
            statusElement.textContent = 'ВКЛ';
            statusElement.className = 'device-status on';
            if (detailsElement) {
                detailsElement.textContent = 'Работает';
            }
        } else {
            // Выключено
            statusElement.textContent = 'ВЫКЛ';
            statusElement.className = 'device-status off';
            if (detailsElement) {
                detailsElement.textContent = 'Отключено';
            }
        }
    }
    
    updateElement(selector, value) {
        const element = document.querySelector(selector);
        if (element) {
            element.textContent = value;
        }
    }
    
    updateProgress(selector, current, target) {
        const element = document.querySelector(selector);
        if (element && target > 0) {
            const percentage = Math.min((current / target) * 100, 100);
            element.style.width = `${percentage}%`;
            
            // Изменить цвет в зависимости от прогресса
            if (percentage < 50) {
                element.style.background = '#e74c3c';
            } else if (percentage < 90) {
                element.style.background = '#f39c12';
            } else {
                element.style.background = '#27ae60';
            }
        }
    }
    
    updateWifiStatus(rssi) {
        const wifiElement = document.getElementById('wifi-rssi');
        if (wifiElement) {
            wifiElement.textContent = `${rssi} dBm`;
            
            // Изменить цвет в зависимости от силы сигнала
            if (rssi > -50) {
                wifiElement.style.color = '#27ae60';
            } else if (rssi > -70) {
                wifiElement.style.color = '#f39c12';
            } else {
                wifiElement.style.color = '#e74c3c';
            }
        }
    }
    
    updateChart(temperatures) {
        const now = Date.now() / 1000; // секунды
        
        // Добавить новые точки
        this.chartData[0].push(now);
        this.chartData[1].push(temperatures.chamber || null);
        this.chartData[2].push(temperatures.target || null);
        this.chartData[3].push(temperatures.product1 || null);
        this.chartData[4].push(temperatures.product2 || null);
        
        // Ограничить количество точек
        const maxPoints = 3600; // 1 час при 1 точке в секунду
        if (this.chartData[0].length > maxPoints) {
            for (let i = 0; i < this.chartData.length; i++) {
                this.chartData[i].shift();
            }
        }
        
        // Обновить график
        if (this.uplot) {
            this.uplot.setData(this.chartData);
        }
        
        // Обновить легенду
        this.updateChartLegend(temperatures);
    }
    
    updateChartLegend(temperatures) {
        const legendHtml = `
            <div class="legend-item">
                <span class="legend-color" style="background: #e74c3c"></span>
                <span class="legend-label">Камера: ${temperatures.chamber ? temperatures.chamber.toFixed(1) : '--'}°C</span>
            </div>
            <div class="legend-item">
                <span class="legend-color" style="background: #f39c12; border: 1px dashed"></span>
                <span class="legend-label">Цель: ${temperatures.target ? temperatures.target.toFixed(1) : '--'}°C</span>
            </div>
            <div class="legend-item">
                <span class="legend-color" style="background: #3498db"></span>
                <span class="legend-label">Продукт 1: ${temperatures.product1 ? temperatures.product1.toFixed(1) : '--'}°C</span>
            </div>
        `;
        
        document.getElementById('chart-legend').innerHTML = legendHtml;
    }
    
    sendCommand(command, data = {}) {
        const payload = JSON.stringify({ command, ...data });
        
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(payload);
            console.log(`Отправлена команда: ${command}`, data);
        } else {
            // Добавить в очередь
            this.commandQueue.push(payload);
            console.log(`Команда добавлена в очередь: ${command} (очередь: ${this.commandQueue.length})`);
            
            // Попытаться переподключиться
            if (this.reconnectAttempts === 0) {
                this.connectWebSocket();
            }
        }
    }
    
    flushCommandQueue() {
        while (this.commandQueue.length > 0 && this.ws.readyState === WebSocket.OPEN) {
            const command = this.commandQueue.shift();
            this.ws.send(command);
            console.log('Команда из очереди отправлена');
        }
    }
    
    sendPing() {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ command: 'ping' }));
        }
    }
    
    updateConnectionStatus(status) {
        const statusTexts = {
            'connected': '🌐 ПОДКЛЮЧЕНО',
            'disconnected': '🌐 ОТКЛЮЧЕНО',
            'error': '🌐 ОШИБКА'
        };
        
        // Можно добавить визуальную индикацию в шапку
    }
    
    updateProgram(program) {
        this.currentProgram = program;
        document.getElementById('current-program').textContent = program.name || 'Не выбрана';
        
        if (program.stages && program.currentStage) {
            const stage = program.stages[program.currentStage - 1];
            if (stage) {
                document.getElementById('stage-info').textContent = stage.name || `Этап ${program.currentStage}`;
                document.getElementById('current-stage').textContent = `${program.currentStage}/${program.stages.length}`;
                
                // Информация о следующем этапе
                const nextStage = program.stages[program.currentStage];
                if (nextStage) {
                    document.getElementById('next-stage-info').textContent = `Следующий: ${nextStage.name}`;
                }
            }
        }
    }
    
    updateLog(logEntry) {
        // Добавить запись в журнал
        const logSection = document.getElementById('log');
        if (logSection.classList.contains('active')) {
            // Обновить таблицу журнала
        }
    }
    
    showUpdateNotification() {
        // Показать уведомление об обновлении
        const notification = document.createElement('div');
        notification.className = 'update-notification';
        notification.innerHTML = `
            <div class="update-content">
                <p>Доступно новое обновление!</p>
                <button onclick="window.location.reload()">Обновить</button>
            </div>
        `;
        document.body.appendChild(notification);
    }
}

// Инициализация при загрузке страницы
window.addEventListener('load', () => {
    window.smoker = new SmokerController();
});