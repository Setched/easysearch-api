FROM python:3.11-slim

WORKDIR /app

# Xvfb provides a virtual display so Chrome can run headful (not headless) inside the
# container — patchright's own docs recommend headful + real Chrome as the least detectable
# configuration, see app/wildberries_session.py's module docstring for why.
RUN apt-get update && apt-get install -y --no-install-recommends xvfb \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && patchright install --with-deps chrome

COPY app ./app

EXPOSE 8001

CMD ["sh", "-c", "Xvfb :99 -screen 0 1920x1080x24 -nolisten tcp & sleep 1 && DISPLAY=:99 exec uvicorn app.main:app --host 0.0.0.0 --port 8001"]
