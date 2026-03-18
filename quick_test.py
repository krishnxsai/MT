import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
fig, ax = plt.subplots(figsize=(2, 2))
ax.text(0.5, 0.5, 'TEST', ha='center', va='center')
fig.savefig('alternate_tj_latex_template_ap/test123.png', dpi=72)
plt.close()
print('SAVED!')
