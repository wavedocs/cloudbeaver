# OIDC Authentication Provider для DBeaver CE
## Интеграция с Keycloak через OpenID Connect

Этот плагин обеспечивает интеграцию DBeaver с внешним Keycloak сервером через OIDC.

## Структура плагина

```
io.cloudbeaver.service.oidc.auth/
├── META-INF/
│   └── MANIFEST.MF              # Манифест OSGI бандла
├── OSGI-INF/
│   └── l10n/
│       └── bundle.properties    # Локализация
├── src/io/cloudbeaver/service/oidc/auth/
│   ├── OidcAuthProvider.java    # Основной класс провайдера аутентификации
│   ├── OidcConstants.java       # Константы
│   └── OidcSettings.java        # Настройки провайдера
├── plugin.xml                   # Конфигурация плагина
├── pom.xml                      # Maven зависимости
└── build.properties             # Build свойства
```

## Сборка

### 1. Добавить модуль в parent pom.xml

Файл уже обновлен: `/workspace/server/bundles/pom.xml`

### 2. Собрать проект

```bash
cd /workspace
mvn clean install -DskipTests
```

### 3. Создать Docker образ

Создайте файл `Dockerfile` в корне проекта или используйте существующий:

```dockerfile
ARG BASE_JAVA_TAG="stable"
FROM dbeaver/base-java:${BASE_JAVA_TAG}

MAINTAINER DBeaver Corp, devops@dbeaver.com

ENV DBEAVER_GID=8978
ENV DBEAVER_UID=8978

RUN groupadd -g $DBEAVER_GID dbeaver && \
    useradd -g $DBEAVER_GID -m -u $DBEAVER_UID -s /bin/bash dbeaver

COPY cloudbeaver /opt/cloudbeaver
COPY scripts/launch-product.sh /opt/cloudbeaver/launch-product.sh

EXPOSE 8978
RUN find /opt/cloudbeaver -type d -exec chmod 775 {} \;
WORKDIR /opt/cloudbeaver/

RUN chmod +x "run-cloudbeaver-server.sh" "/opt/cloudbeaver/launch-product.sh"

ENTRYPOINT ["./launch-product.sh"]
```

## Конфигурация для Kubernetes

### ConfigMap для OIDC настроек

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dbeaver-oidc-config
  namespace: dbeaver
data:
  # Конфигурация OIDC провайдера
  oidc.conf: |
    {
      "provider": "oidc",
      "label": "Keycloak OIDC",
      "icon": "keycloak",
      "configuration": {
        "oidc-issuer-url": "https://keycloak.example.com/auth/realms/dbeaver",
        "oidc-client-id": "dbeaver-client",
        "oidc-client-secret": "${KEYCLOAK_CLIENT_SECRET}",
        "oidc-scope": "openid profile email groups",
        "oidc-redirect-uri": "https://dbeaver.example.com/oidc/callback",
        "oidc-groups-claim": "groups",
        "oidc-username-claim": "preferred_username",
        "oidc-name-claim": "name",
        "oidc-logout-url": "https://keycloak.example.com/auth/realms/dbeaver/protocol/openid-connect/logout"
      }
    }
```

### Deployment с OIDC конфигурацией

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dbeaver-ce
  namespace: dbeaver
  labels:
    app: dbeaver-ce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: dbeaver-ce
  template:
    metadata:
      labels:
        app: dbeaver-ce
    spec:
      containers:
      - name: dbeaver
        image: dbeaver/cloudbeaver:26.1.5
        ports:
        - containerPort: 8978
          name: http
        env:
        - name: KEYCLOAK_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: keycloak-secret
              key: client-secret
        - name: SERVER_PORT
          value: "8978"
        - name: CB_SERVER_NAME
          value: "DBeaver CE"
        volumeMounts:
        - name: oidc-config
          mountPath: /opt/cloudbeaver/conf/oidc.conf
          subPath: oidc.conf
        - name: workspace
          mountPath: /opt/cloudbeaver/workspace
      volumes:
      - name: oidc-config
        configMap:
          name: dbeaver-oidc-config
      - name: workspace
        persistentVolumeClaim:
          claimName: dbeaver-workspace-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: dbeaver-ce-service
  namespace: dbeaver
spec:
  selector:
    app: dbeaver-ce
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8978
  type: ClusterIP
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: dbeaver-ingress
  namespace: dbeaver
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - dbeaver.example.com
    secretName: dbeaver-tls
  rules:
  - host: dbeaver.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: dbeaver-ce-service
            port:
              number: 80
```

## Настройка Keycloak

### 1. Создать Realm

```bash
# Через Keycloak Admin CLI или UI
Realm name: dbeaver
```

### 2. Создать Client

```
Client ID: dbeaver-client
Client Protocol: openid-connect
Access Type: confidential
Standard Flow Enabled: ON
Direct Access Grants Enabled: OFF
Valid Redirect URIs: https://dbeaver.example.com/oidc/callback
Web Origins: https://dbeaver.example.com
```

### 3. Настроить Mapper для Groups

```
Name: groups
Mapper Type: Group Membership
Token Claim Name: groups
Full group path: OFF (чтобы возвращались только имена групп без префикса /)
Add to ID token: ON
Add to access token: ON
Add to userinfo: ON
```

### 4. Создать Группы и Роли

```
Группы:
- admin (полный доступ)
- team1 (доступ к общим подключениям)
- team2 (доступ к общим подключениям)

В DBeaver эти группы будут автоматически замаплены на команды (teams).
```

### 5. Назначить пользователей

Создайте пользователей и назначьте им соответствующие группы:
- Пользователь admin → группа admin
- Пользователь user1 → группа team1
- Пользователь user2 → группа team2

## Конфигурация в web-app.xml (альтернативный способ)

Если вы предпочитаете конфигурировать через XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app>
    <!-- Другие настройки -->
    
    <auth-providers>
        <provider id="oidc" class="io.cloudbeaver.service.oidc.auth.OidcAuthProvider">
            <configuration>
                <property name="oidc-issuer-url" value="https://keycloak.example.com/auth/realms/dbeaver"/>
                <property name="oidc-client-id" value="dbeaver-client"/>
                <property name="oidc-client-secret" value="${KEYCLOAK_CLIENT_SECRET}"/>
                <property name="oidc-scope" value="openid profile email groups"/>
                <property name="oidc-redirect-uri" value="https://dbeaver.example.com/oidc/callback"/>
                <property name="oidc-groups-claim" value="groups"/>
                <property name="oidc-username-claim" value="preferred_username"/>
            </configuration>
        </provider>
    </auth-providers>
</web-app>
```

## Поток аутентификации

1. Пользователь открывает главную страницу DBeaver
2. Нажимает кнопку "Login with OIDC"
3. Перенаправляется на страницу Keycloak
4. Вводит учетные данные
5. Keycloak перенаправляет обратно в DBeaver с authorization code
6. DBeaver обменивает code на access token
7. DBeaver получает информацию о пользователе и группах
8. Пользователь аутентифицирован и добавлен в соответствующие команды

## Важные замечания

1. **Автоматическая регистрация отключена**: Пользователи должны быть предварительно созданы в DBeaver или через админ-панель.

2. **Маппинг ролей**: 
   - Keycloak группа `admin` → DBeaver команда `admin`
   - Keycloak группа `team1` → DBeaver команда `team1`
   - Keycloak группа `team2` → DBeaver команда `team2`

3. **Безопасность**:
   - Используйте HTTPS для всех endpoint'ов
   - Храните client secret в Kubernetes Secrets
   - Регулярно обновляйте сертификаты

4. **Отладка**:
   - Включите debug логи в Keycloak
   - Проверьте JWT токены через https://jwt.io
   - Убедитесь, что redirect URI точно совпадает

## Переменные окружения для Docker/Kubernetes

```yaml
env:
  - name: CB_OIDC_ISSUER_URL
    value: "https://keycloak.example.com/auth/realms/dbeaver"
  - name: CB_OIDC_CLIENT_ID
    value: "dbeaver-client"
  - name: CB_OIDC_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: keycloak-secret
        key: client-secret
  - name: CB_OIDC_REDIRECT_URI
    value: "https://dbeaver.example.com/oidc/callback"
  - name: CB_OIDC_GROUPS_CLAIM
    value: "groups"
  - name: CB_OIDC_USERNAME_CLAIM
    value: "preferred_username"
```

## Тестирование

```bash
# Проверка доступности Keycloak
curl https://keycloak.example.com/auth/realms/dbeaver/.well-known/openid-configuration

# Проверка токена (замените TOKEN на ваш)
curl -H "Authorization: Bearer TOKEN" \
     https://keycloak.example.com/auth/realms/dbeaver/protocol/openid-connect/userinfo
```

## Поддержка

При возникновении проблем:
1. Проверьте логи DBeaver: `/opt/cloudbeaver/logs/`
2. Проверьте логи Keycloak в админ-консоли
3. Убедитесь, что все URI настроены корректно
4. Проверьте сетевую связность между pod'ами
