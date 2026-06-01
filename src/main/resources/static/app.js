const BEHAVIOR_LABELS = {
  ATTENTIVE: "抬头听课",
  HEAD_DOWN: "低头",
  SLEEPING: "睡觉",
  PHONE: "玩手机",
  DISTRACTED: "走神",
  OTHER: "其他",
};

let pieChart;

function setError(message) {
  const el = document.getElementById("error");
  if (!message) {
    el.classList.add("hidden");
    el.textContent = "";
    return;
  }
  el.classList.remove("hidden");
  el.textContent = message;
}

function setLoading(loading) {
  document.getElementById("submitBtn").disabled = loading;
  document.getElementById("fileInput").disabled = loading;
}

function pct(v) {
  const n = Number(v || 0) * 100;
  return `${n.toFixed(1)}%`;
}

function renderResult(payload) {
  const analysis = payload.analysis;
  const previewUrl = payload.previewUrl;

  const img = document.getElementById("previewImg");
  img.src = previewUrl;
  img.style.display = "block";

  document.getElementById("focusScore").textContent = analysis.metrics.focusScore.toFixed(1);
  document.getElementById("attentiveRate").textContent = pct(analysis.metrics.attentiveRate);
  document.getElementById("totalStudents").textContent = analysis.recognition.totalStudents;
  document.getElementById("summary").textContent = analysis.metrics.summary || "-";

  const counts = analysis.recognition.behaviors || {};
  const rates = analysis.metrics.behaviorRates || {};
  const labels = Object.keys(BEHAVIOR_LABELS);
  const data = labels.map(k => Number(counts[k] || 0));

  const tbody = document.querySelector("#behaviorTable tbody");
  tbody.innerHTML = "";
  labels.forEach(k => {
    const tr = document.createElement("tr");
    const td1 = document.createElement("td");
    const td2 = document.createElement("td");
    const td3 = document.createElement("td");
    td1.textContent = BEHAVIOR_LABELS[k] || k;
    td2.textContent = String(counts[k] ?? 0);
    td3.textContent = pct(rates[k] ?? 0);
    tr.appendChild(td1);
    tr.appendChild(td2);
    tr.appendChild(td3);
    tbody.appendChild(tr);
  });

  const ctx = document.getElementById("pieChart");
  const colors = ["#22c55e", "#60a5fa", "#f59e0b", "#ef4444", "#a78bfa", "#94a3b8"];
  if (pieChart) {
    pieChart.data.labels = labels.map(k => BEHAVIOR_LABELS[k] || k);
    pieChart.data.datasets[0].data = data;
    pieChart.update();
  } else {
    pieChart = new Chart(ctx, {
      type: "pie",
      data: {
        labels: labels.map(k => BEHAVIOR_LABELS[k] || k),
        datasets: [{ data, backgroundColor: colors }],
      },
      options: {
        plugins: { legend: { labels: { color: "#e5e7eb" } } },
      },
    });
  }
}

async function uploadAndAnalyze(file) {
  const form = new FormData();
  form.append("file", file);

  const res = await fetch("/api/v1/analyze", { method: "POST", body: form, cache: "no-store" });
  let json;
  try {
    json = await res.json();
  } catch {
    throw new Error("服务返回异常，请刷新后重试");
  }
  if (!json.success) {
    throw new Error(json.message || "请求失败");
  }
  return json.data;
}

document.getElementById("uploadForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  setError("");

  const input = document.getElementById("fileInput");
  if (!input.files || input.files.length === 0) {
    setError("请选择要上传的图片文件");
    return;
  }

  const file = input.files[0];
  setLoading(true);
  try {
    const data = await uploadAndAnalyze(file);
    renderResult(data);
  } catch (err) {
    setError(err.message || "请求失败");
  } finally {
    setLoading(false);
    input.value = "";
  }
});
