import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
print('step1')
fig, ax = plt.subplots(figsize=(2, 2))
plt.savefig('c:/Users/wujjw/Music/MediTrack-main/MediTrack-main/alternate_tj_latex_template_ap/test_out.png', dpi=72)
plt.close()
print('done')
