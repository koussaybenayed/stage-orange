export const HZ_ERROR_TRANSLATIONS: { [raw: string]: string } = {
  'Authorized': 'Autorisé',
  'Temporary Authorized': 'Autorisation temporaire',
  'Pb Hardware': 'Problème matériel (CPE défectueux)',
  '>=3000/S': 'Plus de 3000 connexions/seconde (surcharge)',
  '>=1000/S': 'Plus de 1000 connexions/seconde (surcharge)',
  'EGCI not in Home Zone': 'Attach hors HZ',
  'ECGI Not authorized': 'Attach site fermé',
  'TAC not allowed': 'Attach site fermé',
  'Temporarily Blocked': 'Attach site fermé',
  'IMEI_TAC not allowed': 'SIM Box dans un terminal non autorisé',
  'Session rejected': 'APN non compatible au CPE ou invers',
  'RAT not allowed': 'RAT non autorisé (technologie d\'accès refusée)',
  'No HZ errors': 'Aucune erreur HZ'
};

export function hzErrorTranslation(raw: string): string | undefined {
  return HZ_ERROR_TRANSLATIONS[raw.trim()];
}
