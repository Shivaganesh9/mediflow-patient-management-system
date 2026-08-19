interface BarDatum {
  label: string;
  value: number;
}

export function BarChart({ data, height = 180 }: { data: BarDatum[]; height?: number }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  if (data.length === 0) return null;
  return (
    <div className="bar-chart" style={{ height }}>
      {data.map((d) => (
        <div key={d.label} className="bar-col" title={`${d.label}: ${d.value}`}>
          <div className="bar-fill" style={{ height: `${(d.value / max) * 100}%` }} />
          <span className="bar-value">{d.value}</span>
          <span className="bar-label">{d.label}</span>
        </div>
      ))}
    </div>
  );
}

interface MultiSeries {
  name: string;
  values: number[];
  color: string;
}

export function MultiBarChart({ labels, series }: { labels: string[]; series: MultiSeries[] }) {
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  if (labels.length === 0) return null;
  return (
    <div className="multi-bar-chart">
      <div className="multi-bars">
        {labels.map((label, i) => (
          <div key={label} className="bar-col">
            <div className="bar-group">
              {series.map((s) => (
                <div
                  key={s.name}
                  className="bar-fill"
                  style={{ height: `${(s.values[i] / max) * 100}%`, background: s.color }}
                  title={`${s.name}: ${s.values[i]}`}
                />
              ))}
            </div>
            <span className="bar-label">{label}</span>
          </div>
        ))}
      </div>
      <div className="chart-legend">
        {series.map((s) => (
          <span key={s.name}>
            <i style={{ background: s.color }} />
            {s.name}
          </span>
        ))}
      </div>
    </div>
  );
}