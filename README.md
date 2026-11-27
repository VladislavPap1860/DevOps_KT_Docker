# Контрольная точка по Docker: Интернет-магазин на Java

## 1. Описание проекта и эндпоинты
Учебное веб-приложение / API **Mini Shop** написано на Java (`com.sun.net.httpserver.HttpServer`). Реализован каталог товаров в памяти, корзина с подсчётом суммы и простая HTML-страница для проверки через браузер.

Доступные маршруты и эндпоинты:
- `GET /` - HTML-страница с каталогом товаров и текущим состоянием корзины
- `GET /api/products` - получение списка товаров в формате JSON
- `GET /api/cart` - получение содержимого корзины и общей суммы (`total`)
- `POST /api/cart/add` - добавление товара в корзину по параметру `id`
- `POST /api/cart/clear` - очистка корзины

## 2. Окружение и перенос проекта
Приложение разрабатывалось в IntelliJ IDEA на Windows, а сборка и запуск Docker-контейнера выполнялись в гостевой Linux-системе внутри VirtualBox 
Так как на хосте с Windows движок Docker Desktop не запускался из-за недоступности аппаратной виртуализации, исходный код и `Dockerfile` были перенесены в Linux в каталог проекта (`~/projects/Internet-Shop`).

## 3. Сборка образа
Сборка образа осуществлялась в терминале:
cd ~/projects/Internet-Shop
docker build -t java-shop:1.0 .

## 5. Запуск контейнера и проверка работы
Запуск:
docker run -d --name shop-container -p 8080:8080 java-shop:1.0

Проверка статуса контейнера:
docker ps

Проверка в браузере:
открыть http://localhost:8080 (или http://<IP_виртуалки>:8080 с хоста)

## 6. Просмотр логов контейнера
docker logs shop-container

Результат:
[SHOP] Server started on http://localhost:8080
[SHOP] GET / -> rendered catalog and cart
[SHOP] GET / -> rendered catalog and cart
[SHOP] POST /api/cart/add -> added product id=1 (Retro Cassette Walkman)
[SHOP] GET / -> rendered catalog and cart
[SHOP] POST /api/cart/add -> added product id=2 (Mechanical Keycap Set)
[SHOP] GET / -> rendered catalog and cart
[SHOP] POST /api/cart/add -> added product id=3 (Desk LED Lamp)
[SHOP] GET / -> rendered catalog and cart
[SHOP] POST /api/cart/add -> added product id=4 (USB-C Hub 6-in-1)
[SHOP] GET / -> rendered catalog and cart
[SHOP] POST /api/cart/add -> added product id=1 (Retro Cassette Walkman)
[SHOP] GET / -> rendered catalog and cart
[SHOP] POST /api/cart/clear -> cart cleared
[SHOP] GET / -> rendered catalog and cart
