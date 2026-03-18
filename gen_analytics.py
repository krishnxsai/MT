import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch

fig, ax = plt.subplots(figsize=(16, 9))
ax.set_xlim(0, 16)
ax.set_ylim(0, 9)
ax.axis('off')
fig.patch.set_facecolor('#0d1117')
ax.set_facecolor('#0d1117')

def rbox(ax, x, y, w, h, title, lines, fc, ec, fontsize=7.5, title_fc=None):
    b = FancyBboxPatch((x-w/2, y-h/2), w, h, boxstyle="round,pad=0.12",
                        facecolor=fc, edgecolor=ec, linewidth=1.5)
    ax.add_patch(b)
    if title_fc is None:
        title_fc = ec
    tb = FancyBboxPatch((x-w/2, y+h/2-0.38), w, 0.38,
                         boxstyle="round,pad=0.05", facecolor=title_fc+"55", edgecolor="none")
    ax.add_patch(tb)
    ax.text(x, y+h/2-0.19, title, ha="center", va="center", fontsize=8.5,
            fontweight="bold", color="white")
    for i, line in enumerate(lines):
        ax.text(x, y+h/2-0.65-i*0.28, line, ha="center", va="center",
                fontsize=fontsize, color="#cdd9e5")

def arr(ax, x1, y1, x2, y2, label="", color="#58a6ff", rad=0.0):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="->", color=color, lw=1.6,
                                connectionstyle=f"arc3,rad={rad}"))

# Title
ax.text(8, 8.65, 'MediTrack On-Device Health Analytics Pipeline',
        ha='center', fontsize=14, fontweight='bold', color='white')

# Data Sources (left column)
sources = [
    ('Vital Signs (BP, HR)', '#e63946', 2.0, 6.55),
    ('Blood Glucose / Labs', '#f77f00', 2.0, 5.35),
    ('Medication Adherence', '#2dc653', 2.0, 4.15),
    ('Symptom Journal', '#7209b7', 2.0, 2.95),
]
for t, c, x, y in sources:
    rbox(ax, x, y, 2.6, 0.75, t, [], fc=c+'22', ec=c)
    arr(ax, x+1.3, y, 4.6, y, color=c)

# InsightEngine Facade (center)
rbox(ax, 5.55, 4.75, 2.0, 3.0, 'InsightEngine', [
    '(Facade)', '', 'Coordinates all', 'analytics engines', '',
    'Aggregates &', 'merges outputs'],
    fc='#161b22', ec='#58a6ff', title_fc='#58a6ff', fontsize=7)

# InsightEngine -> 3 sub-engines
arr(ax, 6.55, 6.2, 8.2, 6.8)
arr(ax, 6.55, 4.75, 8.2, 4.75)
arr(ax, 6.55, 3.3, 8.2, 2.8)

# RiskScoreEngine
rbox(ax, 9.6, 6.8, 2.6, 1.4, 'RiskScoreEngine', [
    'Composite 0-100 Score', 'Per-metric weighted', 'Dynamic weighting',
    'Trend-adjusted'],
    fc='#e6394620', ec='#e63946', title_fc='#e63946')

# TrendPredictionEngine
rbox(ax, 9.6, 4.75, 2.6, 1.4, 'TrendPredictionEngine', [
    'Consecutive pattern', 'Linear regression (R2)', 'Slope-based direction',
    'Anomaly flags'],
    fc='#f77f0020', ec='#f77f00', title_fc='#f77f00')

# VitalAlertEngine
rbox(ax, 9.6, 2.7, 2.6, 1.4, 'VitalAlertEngine', [
    'Threshold-based alerts', 'Per-vital calibration',
    'Severity: LOW/MED/HIGH', 'Time-of-day context'],
    fc='#7209b720', ec='#7209b7', title_fc='#7209b7')

# Sub-engines -> Prioritizer
arr(ax, 10.9, 6.8, 12.5, 5.8, color='#e63946')
arr(ax, 10.9, 4.75, 12.5, 5.35, color='#f77f00')
arr(ax, 10.9, 2.7, 12.5, 4.85, color='#7209b7')

# UnifiedAlertPrioritizer
rbox(ax, 13.5, 5.35, 2.5, 1.3, 'UnifiedAlert Prioritizer', [
    'De-duplicates alerts', 'Priority ranking',
    'Rate-limiting', 'Groups by category'],
    fc='#0096c720', ec='#0096c7', title_fc='#0096c7')

arr(ax, 13.5, 4.7, 13.5, 3.85, color='#0096c7')

# HealthInsightEngine
rbox(ax, 13.5, 3.2, 2.5, 1.2, 'HealthInsight Engine', [
    'Personalized suggestions', 'Doctor-review flags',
    'Lifestyle recs', 'Med review prompts'],
    fc='#2dc65320', ec='#2dc653', title_fc='#2dc653')

arr(ax, 13.5, 2.6, 13.5, 1.9, color='#2dc653')

# Output
rbox(ax, 13.5, 1.45, 2.5, 0.75, 'Patient / Doctor UI', [
    'RiskDashboard  InsightCards  Alerts'],
    fc='#58a6ff20', ec='#58a6ff', title_fc='#58a6ff')

# Legend
patches = [
    mpatches.Patch(color='#e63946', label='Risk Scoring'),
    mpatches.Patch(color='#f77f00', label='Trend Prediction'),
    mpatches.Patch(color='#7209b7', label='Vital Alerts'),
    mpatches.Patch(color='#0096c7', label='Alert Prioritization'),
    mpatches.Patch(color='#2dc653', label='Personalized Insights'),
    mpatches.Patch(color='#58a6ff', label='Orchestration / Output'),
]
ax.legend(handles=patches, loc='lower left', fontsize=7.5,
          framealpha=0.4, facecolor='#161b22', edgecolor='#444',
          labelcolor='white', ncol=3)

plt.tight_layout(pad=0.5)
plt.savefig(r'c:/Users/wujjw/Music/MediTrack-main/MediTrack-main/alternate_tj_latex_template_ap/fig_analytics.png',
            dpi=180, bbox_inches='tight', facecolor='#0d1117')
plt.close()
print('fig_analytics.png saved')
