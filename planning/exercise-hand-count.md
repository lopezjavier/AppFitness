# Exercise hand count (one hand / two hands)

Fill the **Por mano** column: `sí` = the logged weight is per hand/side (the volume counts it twice), `no` = the logged weight is the total. Pre-filled with a proposal (dumbbell and kettlebell `sí`, the rest `no`); correct what is wrong. Barbell and bodyweight exercises are not listed (always `no`). Tell Claude when it is filled (ADR-56).

Equipment legend: dumbbell = mancuerna, kettlebell = pesa rusa, machine = máquina, cable = polea, band = banda, other = otro.

## dumbbell (86)

| Key | Name | Muscle group | Por mano |
|---|---|---|---|
| incline-dumbbell-fly | Apertura con mancuernas en banco inclinado | chest | sí |
| rear-delt-fly | Apertura de Deltoides Posterior | shoulders | sí |
| db-fly | Aperturas con Mancuernas | chest | sí |
| decline-db-fly | Aperturas con Mancuernas en Banco Declinado | chest | sí |
| dumbbell-farmers-walk | Caminata del Granjero con Mancuernas | full_body | sí |
| dumbbell-clean-and-press | Clean and Press con Mancuernas | full_body | sí |
| spider-curl | Curl Araña | biceps | sí |
| strict-curl | Curl Estricto | biceps | sí |
| db-reverse-curl | Curl Inverso con Mancuernas | biceps | sí |
| cross-body-hammer-curl | Curl Martillo Cruzado | biceps | sí |
| preacher-hammer-curl | Curl Martillo en Banco Scott | biceps | sí |
| one-arm-dumbbell-preacher-curl | Curl Predicador con Mancuerna a Un Brazo | biceps | sí |
| db-preacher-curl | Curl Predicador con Mancuernas | biceps | sí |
| zottman-curl | Curl Zottman | biceps | sí |
| seated-dumbbell-curl | Curl con Mancuernas Sentado | biceps | sí |
| incline-db-curl | Curl con mancuernas en banco inclinado | biceps | sí |
| bicep-curl | Curl de Bíceps con Mancuernas | biceps | sí |
| concentration-curl | Curl de Concentración | biceps | sí |
| hammer-curl | Curl de Martillo con Mancuernas | biceps | sí |
| db-reverse-wrist-curl | Curl de Muñeca Inverso con Mancuerna | forearms | sí |
| dumbbell-wrist-curl | Curl de Muñeca con Mancuerna | forearms | sí |
| wrist-curl | Curl de Muñeca con Mancuernas Bilateral | forearms | sí |
| incline-hammer-curl | Curl martillo en banco inclinado | biceps | sí |
| single-arm-hammer-curl | Curl martillo unilateral | biceps | sí |
| dumbbell-curl-and-press | Curl y Press con Mancuernas | shoulders | sí |
| dumbbell-front-raise | Elevación Frontal con Mancuernas | shoulders | sí |
| side-lying-lateral-raise | Elevación Lateral Tumbado de Lado | shoulders | sí |
| seated-dumbbell-lateral-raise | Elevación Lateral con Mancuerna Sentado | shoulders | sí |
| lateral-raise | Elevación Lateral con Mancuernas | shoulders | sí |
| dumbbell-calf-raise | Elevación de Talones con Mancuernas | calves | sí |
| dumbbell-hip-thrust | Empuje de Cadera con Mancuerna | glutes | sí |
| db-shrug | Encogimiento de Hombros con Mancuernas | back | sí |
| chest-supported-dumbbell-shrug | Encogimiento de Hombros con Mancuernas y Apoyo en el Pecho | back | sí |
| lying-tricep-extension | Extensión de Tríceps Tumbado | triceps | sí |
| dumbbell-tricep-extension | Extensión de Tríceps con Mancuerna | triceps | sí |
| seated-dumbbell-tricep-extension | Extensión de Tríceps con Mancuerna Sentado | triceps | sí |
| overhead-tricep-extension | Extensión de Tríceps por Encima de la Cabeza | triceps | sí |
| single-arm-dumbbell-overhead-tricep-extension | Extensión de Tríceps sobre la Cabeza con Mancuerna a Un Brazo | triceps | sí |
| dumbbell-face-pull | Face Pull con Mancuernas | shoulders | sí |
| dumbbell-side-bend | Flexión Lateral con Mancuerna | core | sí |
| jefferson-curl | Jefferson curl | back | sí |
| dumbbell-windmill | Molino con Mancuerna | full_body | sí |
| double-db-overhead-carry | Paseo con Dos Mancuernas sobre la Cabeza | shoulders | sí |
| db-overhead-carry | Paseo con Mancuerna sobre la Cabeza | shoulders | sí |
| tricep-kickback | Patada de Tríceps con Mancuerna | triceps | sí |
| double-db-kickstand-deadlift | Peso Muerto Kickstand con Doble Mancuerna | glutes | sí |
| db-kickstand-deadlift | Peso Muerto Kickstand con Mancuerna | glutes | sí |
| single-leg-romanian-deadlift | Peso Muerto Rumano a Una Pierna | glutes | sí |
| one-arm-single-leg-dumbbell-romanian-deadlift | Peso Muerto Rumano a Una Pierna con Mancuerna a Un Brazo | glutes | sí |
| dumbbell-romanian-deadlift | Peso Muerto Rumano con Mancuernas | glutes | sí |
| dumbbell-deadlift | Peso Muerto con Mancuernas | back | sí |
| arnold-press | Press Arnold | shoulders | sí |
| db-svend-press | Press Svend con Mancuernas | chest | sí |
| single-db-svend-press | Press Svend con una Mancuerna | chest | sí |
| incline-db-press | Press con mancuernas en banco inclinado | chest | sí |
| decline-bench-press | Press de Banca Declinado | chest | sí |
| single-arm-dumbbell-bench-press | Press de Banca con Mancuerna a Un Brazo | chest | sí |
| db-bench-press | Press de Banca con Mancuernas | chest | sí |
| close-grip-db-bench-press | Press de Banca con Mancuernas y Agarre Cerrado | triceps | sí |
| dumbbell-push-press | Press de Empuje con Mancuernas | shoulders | sí |
| seated-db-press | Press de Hombro con Mancuernas Sentado | shoulders | sí |
| one-arm-dumbbell-shoulder-press | Press de Hombros con Mancuerna a Un Brazo | shoulders | sí |
| dumbbell-shoulder-press | Press de Hombros con Mancuernas | shoulders | sí |
| dumbbell-floor-press | Press de Suelo con Mancuernas | chest | sí |
| db-pullover | Pullover con Mancuerna | back | sí |
| one-arm-dumbbell-push-press | Push Press con Mancuerna a Un Brazo | shoulders | sí |
| dumbbell-reverse-fly | Pájaro Invertido con Mancuernas | shoulders | sí |
| renegade-row | Remo Renegado | back | sí |
| dumbbell-upright-row | Remo al Mentón con Mancuernas | shoulders | sí |
| single-arm-db-row | Remo con Mancuerna a Un Brazo | back | sí |
| single-arm-chest-supported-dumbbell-row | Remo con Mancuerna a Una Mano con Pecho Apoyado | back | sí |
| bent-over-db-row | Remo con Mancuernas Inclinado | back | sí |
| chest-supported-db-row | Remo con Mancuernas con Apoyo en el Pecho | back | sí |
| dumbbell-bench-pull | Remo en Banco con Mancuernas | back | sí |
| bulgarian-split-squat | Sentadilla Búlgara | glutes | sí |
| dumbbell-front-squat | Sentadilla Frontal con Mancuernas | glutes | sí |
| dumbbell-pistol-squat | Sentadilla Pistola con Mancuerna | glutes | sí |
| db-somersault-squat | Sentadilla Somersault con Mancuerna | glutes | sí |
| db-sumo-squat | Sentadilla Sumo con Mancuernas | glutes | sí |
| db-squat | Sentadilla con Mancuernas | glutes | sí |
| dumbbell-split-squat | Sentadilla en Postura Dividida con Mancuernas | glutes | sí |
| db-skull-crusher | Skull Crusher con Mancuernas | triceps | sí |
| dumbbell-snatch | Snatch con Mancuerna | full_body | sí |
| one-arm-dumbbell-swing | Swing con Mancuerna a un Brazo | full_body | sí |
| reverse-lunge | Zancada Inversa | glutes | sí |
| db-lunge | Zancada con Mancuernas | glutes | sí |

## kettlebell (62)

| Key | Name | Muscle group | Por mano |
|---|---|---|---|
| double-kettlebell-clean-and-press | Clean and press con doble kettlebell | shoulders | sí |
| double-kettlebell-clean | Clean con Dos Pesas Rusas | glutes | sí |
| double-kettlebell-bicep-curl | Curl de Bíceps con Dos Pesas Rusas | biceps | sí |
| one-arm-kettlebell-bicep-curl | Curl de Bíceps con Pesa Rusa a Un Brazo | biceps | sí |
| kettlebell-reverse-wrist-curl | Curl de Muñeca Inverso con Kettlebell | forearms | sí |
| kettlebell-wrist-curl | Curl de Muñeca con Kettlebell | forearms | sí |
| kettlebell-concentration-curl | Curl de concentración con kettlebell | biceps | sí |
| kettlebell-hammer-curl | Curl martillo con pesa rusa | biceps | sí |
| double-kettlebell-dead-clean | Dead Clean con Dos Pesas Rusas | full_body | sí |
| double-kettlebell-dead-split-snatch | Dead Split Snatch con Dos Pesas Rusas | full_body | sí |
| kettlebell-tibialis-raise | Elevación de Tibial con Pesa Rusa | calves | sí |
| kettlebell-hip-thrust | Empuje de Cadera con Kettlebell | glutes | sí |
| kettlebell-shrug | Encogimiento de Hombros con Kettlebell | back | sí |
| one-arm-kettlebell-tricep-kickback | Extensión de Tríceps a Un Brazo con Pesa Rusa | triceps | sí |
| kettlebell-overhead-tricep-extension | Extensión de tríceps por encima de la cabeza con pesa rusa | triceps | sí |
| kettlebell-russian-twist | Giro ruso con pesa rusa | core | sí |
| kettlebell-halo | Halo con pesa rusa | shoulders | sí |
| kettlebell-sumo-high-pull | Jalón sumo al mentón con pesa rusa | quads | sí |
| double-kettlebell-jerk | Jerk con Dos Pesas Rusas | shoulders | sí |
| kettlebell-turkish-get-ups | Levantamiento Turco con Kettlebell | full_body | sí |
| kettlebell-windmills | Molino de Viento con Pesa Rusa | full_body | sí |
| double-kettlebell-overhead-carry | Paseo con Dos Kettlebells sobre la Cabeza | shoulders | sí |
| kettlebell-overhead-carry | Paseo con Kettlebell sobre la Cabeza | shoulders | sí |
| kettlebell-farmers-walk | Paseo del Granjero con Pesa Rusa | full_body | sí |
| suitcase-carry | Paseo del Maletín | forearms | sí |
| kettlebell-kickstand-deadlift | Peso Muerto Kickstand con Kettlebell | glutes | sí |
| one-arm-single-leg-kettlebell-romanian-deadlift | Peso Muerto Rumano a Una Pierna con Pesa Rusa a Un Brazo | glutes | sí |
| kettlebell-single-leg-deadlift | Peso muerto a una pierna con pesa rusa | glutes | sí |
| kettlebell-deadlift | Peso muerto con pesa rusa | back | sí |
| kettlebell-sumo-deadlift | Peso muerto sumo con pesa rusa | glutes | sí |
| one-arm-kettlebell-bottoms-up-press | Press Strict con Pesa Rusa Invertida a Un Brazo | shoulders | sí |
| kettlebell-svend-press | Press Svend con Kettlebells | chest | sí |
| one-arm-kettlebell-shoulder-press | Press de Hombro con Pesa Rusa a Un Brazo | shoulders | sí |
| kettlebell-close-grip-floor-press | Press de Suelo con Agarre Cerrado con Pesa Rusa | triceps | sí |
| kettlebell-floor-press | Press de suelo con pesas rusas | chest | sí |
| one-arm-kettlebell-floor-glute-bridge-press | Press en Puente de Glúteos en Suelo con Pesa Rusa a Un Brazo | glutes | sí |
| one-arm-kettlebell-floor-press | Press en Suelo con Pesa Rusa a Un Brazo | chest | sí |
| double-kettlebell-overhead-press | Press por Encima de la Cabeza con Dos Pesas Rusas | shoulders | sí |
| floor-kettlebell-pullover | Pullover con Pesa Rusa en Suelo | back | sí |
| kettlebell-pullover | Pullover con pesa rusa | back | sí |
| double-kettlebell-push-press | Push Press con Dos Pesas Rusas | shoulders | sí |
| one-arm-kettlebell-push-press | Push Press con Pesa Rusa a Un Brazo | shoulders | sí |
| double-kettlebell-row | Remo con Dos Pesas Rusas | back | sí |
| double-kettlebell-rear-delt-row | Remo con Dos Pesas Rusas para Deltoides Posterior | shoulders | sí |
| chest-supported-kettlebell-row | Remo con Kettlebell Apoyado en el Pecho | back | sí |
| one-arm-kettlebell-row | Remo con Pesa Rusa a Un Brazo | back | sí |
| kettlebell-bulgarian-split-squat | Sentadilla Búlgara con Kettlebell | glutes | sí |
| one-arm-kettlebell-front-squat | Sentadilla Frontal con Pesa Rusa a Un Brazo | glutes | sí |
| goblet-squat | Sentadilla Goblet | glutes | sí |
| kettlebell-squat | Sentadilla con Kettlebell | quads | sí |
| kettlebell-pistol-squat | Sentadilla pistola con pesa rusa | glutes | sí |
| kettlebell-skull-crusher | Skull crusher con pesa rusa | triceps | sí |
| double-kettlebell-split-jerk | Split Jerk con Dos Pesas Rusas | full_body | sí |
| double-kettlebell-swing-snatch | Swing Snatch con Dos Pesas Rusas | full_body | sí |
| one-arm-kettlebell-swing | Swing con Pesa Rusa a Un Brazo | full_body | sí |
| kettlebell-swing | Swing con pesa rusa | glutes | sí |
| kettlebell-swing-clean | Swing y cargada con pesa rusa | full_body | sí |
| kettlebell-goblet-lunge | Zancada Goblet con Kettlebell | glutes | sí |
| kettlebell-reverse-lunge | Zancada Inversa con Kettlebell | glutes | sí |
| kettlebell-lunge-press | Zancada con press de pesa rusa | full_body | sí |
| kettlebell-offset-reverse-lunge-and-press | Zancada inversa con press desequilibrado de pesa rusa | full_body | sí |
| kettlebell-rotational-lunge | Zancada rotacional con pesa rusa | glutes | sí |

## machine (69)

| Key | Name | Muscle group | Por mano |
|---|---|---|---|
| machine-seated-crunch | Abdominales en Máquina Sentado | core | no |
| hip-abduction | Abducción de Cadera en Máquina | glutes | no |
| hip-adduction | Aducción de cadera | quads | no |
| machine-reverse-fly | Apertura Inversa en Máquina | shoulders | no |
| machine-chest-fly | Aperturas en Máquina | chest | no |
| stationary-bike | Bicicleta Estática | quads | no |
| air-bike | Bicicleta de Aire | full_body | no |
| smith-machine-good-morning | Buenos Días en Máquina Smith | back | no |
| incline-treadmill-walk | Caminata en Cinta Inclinada | full_body | no |
| treadmill-running | Carrera en Cinta | full_body | no |
| nordic-hamstring-curl | Curl Nórdico de Isquiotibiales | hamstrings | no |
| machine-bicep-curl | Curl de Bíceps en Máquina | biceps | no |
| single-leg-lying-leg-curl | Curl de Pierna Tumbado a Una Pierna | hamstrings | no |
| seated-leg-curl | Curl de Piernas Sentado | hamstrings | no |
| leg-curl | Curl de Piernas Tumbado | hamstrings | no |
| machine-preacher-curl | Curl en Máquina de Predicador | biceps | no |
| assisted-pull-ups | Dominadas Asistidas | back | no |
| single-arm-plate-loaded-lateral-raise | Elevación Lateral a Un Brazo en Máquina de Discos | shoulders | no |
| plate-loaded-lateral-raise | Elevación Lateral en Máquina de Discos | shoulders | no |
| plate-loaded-donkey-calf-raise | Elevación de Pantorrilla a la Burra en Máquina | calves | no |
| machine-calf-raise | Elevación de Pantorrillas en Máquina | calves | no |
| seated-calf-raise | Elevación de Talones Sentado | calves | no |
| standing-calf-raise | Elevación de Talones de Pie | calves | no |
| hack-squat-calf-raise | Elevación de Talones en Máquina Hack Squat | calves | no |
| smith-machine-calf-raise | Elevación de Talón en Máquina Smith | calves | no |
| elliptical-trainer | Elíptica | full_body | no |
| plate-loaded-glute-drive | Empuje de Glúteo en Máquina de Discos | glutes | no |
| smith-machine-shrug | Encogimiento de Hombros en Máquina Smith | back | no |
| plate-loaded-shrug | Encogimiento de Hombros en Máquina de Discos | back | no |
| stair-climber | Escaladora de Escaleras | glutes | no |
| single-leg-extension | Extensión de Cuádriceps a Una Pierna | quads | no |
| machine-back-extension | Extensión de Espalda en Máquina | back | no |
| leg-extension | Extensión de Piernas | quads | no |
| machine-triceps-extension | Extensión de Tríceps en Máquina | triceps | no |
| assisted-dips | Fondos en Máquina Asistida | chest | no |
| hack-squat | Hack Squat | glutes | no |
| smith-machine-hip-thrust | Hip Thrust en Máquina Smith | glutes | no |
| behind-the-neck-lat-pulldown | Jalón al Cuello Detrás de la Cabeza | back | no |
| v-bar-lat-pulldown | Jalón al Pecho con Barra en V | back | no |
| seated-dip-machine | Máquina de Fondos Sentado | triceps | no |
| plate-loaded-seated-dip-machine | Máquina de Fondos Sentado con Discos | triceps | no |
| rowing-machine | Máquina de Remo | full_body | no |
| pec-deck | Pec Deck | chest | no |
| smith-machine-rdl | Peso Muerto Rumano en Máquina Smith | hamstrings | no |
| leg-press | Prensa de Piernas | glutes | no |
| single-leg-press | Prensa de Piernas a Una Pierna | glutes | no |
| high-foot-leg-press | Prensa de piernas con pies altos | glutes | no |
| wide-stance-leg-press | Prensa de piernas con postura amplia | quads | no |
| close-stance-leg-press | Prensa de piernas con postura cerrada | quads | no |
| horizontal-leg-press | Prensa de piernas horizontal | glutes | no |
| smith-machine-incline-bench-press | Press Inclinado en Máquina Smith | shoulders | no |
| smith-machine-decline-bench-press | Press de Banca Declinado en Máquina Smith | chest | no |
| smith-machine-bench-press | Press de Banca en Máquina Smith | chest | no |
| machine-shoulder-press | Press de Hombros en Máquina | shoulders | no |
| smith-machine-shoulder-press | Press de Hombros en Máquina Smith | shoulders | no |
| seated-smith-machine-shoulder-press | Press de Hombros en Máquina Smith Sentado | shoulders | no |
| chest-press-machine | Press de Pecho en Máquina | chest | no |
| single-arm-machine-shoulder-press | Press de hombros unilateral en máquina | shoulders | no |
| smith-machine-reverse-grip-bent-over-row | Remo Inclinado con Agarre Inverso en Máquina Smith | biceps | no |
| smith-machine-bent-over-row | Remo Inclinado en Máquina Smith | back | no |
| smith-machine-upright-row | Remo al Mentón en Máquina Smith | shoulders | no |
| chest-supported-smith-machine-row | Remo con Apoyo en el Pecho en Máquina Smith | back | no |
| chest-supported-machine-row | Remo en Máquina con Apoyo en el Pecho | back | no |
| smith-machine-bulgarian-split | Sentadilla Búlgara en Máquina Smith | glutes | no |
| smith-machine-front-squat | Sentadilla Frontal en Máquina Smith | glutes | no |
| smith-machine-squat | Sentadilla en Máquina Smith | glutes | no |
| smith-machine-split-squat | Sentadilla en Tijera en Máquina Smith | glutes | no |
| ski-erg | SkiErg | full_body | no |
| smith-machine-reverse-lunge | Zancada Inversa en Máquina Smith | glutes | no |

## cable (30)

| Key | Name | Muscle group | Por mano |
|---|---|---|---|
| cable-fly | Aperturas con Cable | chest | no |
| cable-crunch | Crunch con Cable | core | no |
| cable-curl | Curl con Cable | biceps | no |
| cable-hammer-curl | Curl de Martillo en Cable | biceps | no |
| cable-wrist-curl | Curl de Muñeca con Cable | forearms | no |
| cable-front-raise | Elevación Frontal con Cable | shoulders | no |
| cable-lateral-raise | Elevación Lateral en Polea | shoulders | no |
| straight-bar-cable-front-raise | Elevación frontal en polea con barra recta | shoulders | no |
| single-arm-tricep-pushdown | Extensión de Tríceps a Un Brazo en Polea | triceps | no |
| v-bar-tricep-pushdown | Extensión de Tríceps en Polea con Barra en V | triceps | no |
| cable-overhead-tricep-extension | Extensión de Tríceps por Encima de la Cabeza con Cable | triceps | no |
| face-pull | Face Pull en Polea | shoulders | no |
| lat-pulldown | Jalón al Pecho | back | no |
| reverse-grip-lat-pulldown | Jalón al Pecho Agarre Supino | biceps | no |
| close-grip-lat-pulldown | Jalón al Pecho con Agarre Cerrado | back | no |
| one-arm-lat-pulldown | Jalón al Pecho con Un Brazo | back | no |
| straight-arm-pulldown | Jalón con Brazos Rectos | back | no |
| tricep-pushdown | Jalón de Tríceps en Cable | triceps | no |
| cable-kickback | Patada Trasera de Glúteos en Cable | glutes | no |
| cable-tricep-kickback | Patada de Tríceps con Cable | triceps | no |
| cable-pallof-press | Press Pallof en Polea | core | no |
| cable-chest-press | Press de Pecho con Cable | chest | no |
| cable-pull-through | Pull-Through en Polea | glutes | no |
| cable-bent-over-row | Remo Inclinado con Cable | back | no |
| seated-cable-row | Remo Sentado en Polea | back | no |
| one-arm-seated-cable-row | Remo Unilateral Sentado en Polea | back | no |
| cable-upright-row | Remo al Mentón con Cable | shoulders | no |
| kneeling-cable-row | Remo en Polea Arrodillado | back | no |
| wide-grip-seated-cable-row | Remo en Polea Sentado en Agarre Ancho | back | no |
| cable-external-rotation | Rotación Externa con Cable | shoulders | no |

## band (28)

| Key | Name | Muscle group | Por mano |
|---|---|---|---|
| banded-seated-hip-abduction | Abducción de Cadera Sentado con Banda | glutes | no |
| banded-standing-hip-abduction | Abducción de Cadera de Pie con Banda | glutes | no |
| banded-standing-hip-adduction | Aducción de Cadera de Pie con Banda | quads | no |
| banded-clamshell | Almeja con Banda | glutes | no |
| banded-good-morning | Buenos Días con Banda | glutes | no |
| banded-lateral-walk | Caminata Lateral con Banda | glutes | no |
| banded-sumo-walk | Caminata Sumo con Banda | glutes | no |
| banded-standing-curl | Curl de Pierna de Pie con Banda | hamstrings | no |
| band-assisted-pull-ups | Dominadas Asistidas con Banda | back | no |
| banded-kneeling-hip-thrust | Empuje de Cadera Arrodillado con Banda | glutes | no |
| banded-hip-thrust | Empuje de Cadera con Banda | glutes | no |
| banded-figure-4-stretch | Estiramiento Figura 4 con Banda | glutes | no |
| banded-adductor-stretch | Estiramiento de Aductores con Banda | quads | no |
| banded-it-band-stretch | Estiramiento de Banda Iliotibial con Banda | quads | no |
| banded-rear-delt-stretch | Estiramiento de Deltoides Posterior con Banda | shoulders | no |
| banded-lat-stretch | Estiramiento de Dorsales con Banda | back | no |
| banded-shoulder-stretch | Estiramiento de Hombros con Banda | shoulders | no |
| banded-hamstring-stretch | Estiramiento de Isquiotibiales con Banda | hamstrings | no |
| banded-calf-stretch | Estiramiento de Pantorrilla con Banda | calves | no |
| banded-chest-stretch | Estiramiento de Pecho con Banda | chest | no |
| banded-ankle-stretch | Estiramiento de Tobillo con Banda | calves | no |
| banded-triceps-stretch | Estiramiento de Tríceps con Banda | triceps | no |
| banded-terminal-knee-extension | Extensión Terminal de Rodilla con Banda | quads | no |
| banded-fire-hydrant | Hidrante de Fuego con Banda | glutes | no |
| banded-romanian-deadlift | Peso Muerto Rumano con Banda | glutes | no |
| banded-glute-bridge | Puente de Glúteos con Banda | glutes | no |
| banded-squat | Sentadilla con Banda | glutes | no |
| band-pull-apart | Separación de Banda Elástica | shoulders | no |

## other (31)

| Key | Name | Muscle group | Por mano |
|---|---|---|---|
| plate-pinch | Agarre de Discos | forearms | no |
| weighted-wall-crunch | Crunch en Pared con Peso | core | no |
| battle-ropes | Cuerdas de Batalla | full_body | no |
| ball-leg-curl | Curl de Piernas con Pelota de Estabilidad | hamstrings | no |
| stability-ball-knee-tuck | Encogimiento de Rodillas con Balón de Estabilidad | core | no |
| bench-bulgarian-split-stretch | Estiramiento Búlgaro en Banco | quads | no |
| bench-couch-stretch | Estiramiento Couch en Banco | quads | no |
| bench-figure-4-glute-stretch | Estiramiento Figura 4 de Glúteos en Banco | glutes | no |
| bench-adductor-stretch | Estiramiento de Aductores en Banco | quads | no |
| bench-lat-stretch | Estiramiento de Dorsales en Banco | back | no |
| bench-hamstring-stretch | Estiramiento de Isquiotibiales en Banco | hamstrings | no |
| bench-calf-stretch | Estiramiento de Pantorrilla en Banco | calves | no |
| bench-chest-stretch | Estiramiento de Pecho en Banco | chest | no |
| bench-ankle-stretch | Estiramiento de Tobillo en Banco | calves | no |
| stability-ball-push-up-hands-on-ball | Flexión sobre pelota de estabilidad (manos en la pelota) | chest | no |
| battle-rope-double-slam | Golpe Doble con Cuerdas de Batalla | full_body | no |
| medicine-ball-slam | Golpe con Balón Medicinal | core | no |
| bench-hollow-hold | Mantenimiento del Cuerpo Hueco en Banco | quads | no |
| ball-pike | Pike con Pelota | core | no |
| bench-childs-pose | Postura del Niño en Banco | back | no |
| svend-press | Press Svend | chest | no |
| stability-ball-hip-bridge | Puente de Cadera con Balón de Estabilidad | glutes | no |
| plate-pullover | Pullover con Disco | back | no |
| stability-ball-push-up | Push-Up con Balón de Estabilidad | chest | no |
| sled-row | Remo con Trineo | back | no |
| wrist-roller | Rodillo de Muñeca | forearms | no |
| ab-wheel-rollout | Rueda Abdominal | core | no |
| jump-rope | Saltar la Cuerda | full_body | no |
| box-jump | Salto al Cajón | glutes | no |
| stability-ball-wall-squat | Sentadilla en Pared con Pelota | glutes | no |
| rope-climb | Trepa de Cuerda | back | no |
