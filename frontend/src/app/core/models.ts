export interface PricingTier {
  name: string;
  detail: string;
  price: string;
  /** "What's included" bullets, shown behind a toggle on the pricing page. */
  includes: string[];
  /** How long this package blocks the calendar (minutes). FLAT packages only. */
  durationMinutes: number;
  /** FLAT: fixed length for `price`. HOURLY: `price` is per hour and the customer picks minHours…maxHours. */
  pricingModel: 'FLAT' | 'HOURLY';
  minHours: number;
  maxHours: number;
  addons: PricingAddOn[];
}

export interface PricingAddOn {
  name: string;
  price: number;
  /** Charged per hour of the session instead of once. */
  perHour: boolean;
  /** Brings another photographer along (the session then needs a lead and a second). */
  extraPhotographer: boolean;
}

export interface SessionType {
  key: string;
  name: string;
  /** Short label for filter chips and captions. */
  shortName: string;
  description: string;
  from: string;
  /** Portfolio page: show a filter chip and three photo slots for this category. */
  showInPortfolio: boolean;
  /** How long a session blocks the calendar; booking slots are offered at this length. */
  durationMinutes: number;
  tiers: PricingTier[];
}

export interface SessionTypeCreate {
  name: string;
  shortName: string;
  description: string;
  from: string;
  showInPortfolio: boolean;
  durationMinutes: number;
}

/** Booking window for one weekday (0 = Sunday … 6 = Saturday); times "HH:mm". */
export interface AvailabilityHours {
  weekday: number;
  open: boolean;
  start: string;
  end: string;
}

/** A week of booking hours: the studio's (staffName null) or one person's own (custom = they have their own week). */
export interface HoursWeek {
  staffName: string | null;
  custom: boolean;
  hours: AvailabilityHours[];
}

/** Time the studio is unavailable. Times null = whole day(s). The note is private. */
export interface TimeBlock {
  id: number;
  startDate: string;
  endDate: string;
  startTime: string | null;
  endTime: string | null;
  note: string | null;
  /** Whose time; null = the whole studio. */
  staffName: string | null;
}

export interface TimeBlockCreate {
  startDate: string;
  endDate: string | null;
  startTime: string | null;
  endTime: string | null;
  note: string;
  staffName: string | null;
}

/** Insights report from /api/admin/analytics. */
export interface AnalyticsReport {
  days: number;
  totals: { visitors: number; visits: number; pageviews: number; avgVisitSeconds: number; bounceRate: number };
  daily: { date: string; visitors: number; visits: number }[];
  pages: { label: string; count: number }[];
  referrers: { label: string; count: number }[];
  devices: { label: string; count: number }[];
  funnel: { key: string; label: string; visits: number }[];
  bookingsSubmitted: number;
}

/** Direct contact details from the API (configured on the server). */
export interface ContactInfo {
  email: string | null;
  phone: string | null;
  instagram: string | null;
  location: string | null;
}

export interface Slot {
  /** "HH:mm" */
  start: string;
  /** "9:00 AM" */
  label: string;
}

export interface DaySlots {
  date: string;
  slots: Slot[];
}

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED';

export interface Booking {
  id: number;
  sessionType: string;
  sessionTypeName: string;
  packageName: string | null;
  hours: number | null;
  addons: string[];
  name: string;
  email: string;
  phone: string | null;
  location: string | null;
  notes: string | null;
  /** ISO date, yyyy-mm-dd */
  date: string;
  /** Display label, e.g. "9:00 AM" */
  time: string;
  /** "HH:mm" slot bounds; null on rows from before scheduling existed. */
  startTime: string | null;
  endTime: string | null;
  status: BookingStatus;
  quote: number | null;
  createdAt: string;
  /** Everyone on the session, lead first. */
  assignees: string[];
  lead: string | null;
  second: string | null;
  /** The package/add-ons call for two photographers. */
  needsSecond: boolean;
}

export interface BookingRequest {
  sessionType: string;
  /** Package (tier) name; required when the session type has packages. */
  packageName: string | null;
  /** Hourly packages: how many hours. */
  hours: number | null;
  /** Chosen add-on names. */
  addons: string[];
  /** A bookable photographer's name, or null for no preference. */
  photographer: string | null;
  name: string;
  email: string;
  phone: string;
  location: string;
  notes: string;
  date: string;
  time: string;
  /** Honeypot — must stay empty. */
  website: string;
}

export interface PublicPopupEvent {
  id: number;
  date: string;
  title: string;
  theme: string;
  venue: string;
  /** Street address, optional; linked to a map on the Events page. */
  address: string | null;
  time: string;
}

export interface PopupEvent extends PublicPopupEvent {
  prints: number;
  printPrice: number;
}

export interface PopupEventUpsert {
  date: string;
  title: string;
  theme: string;
  venue: string;
  address: string;
  time: string;
}

export interface Gallery {
  id: number;
  bookingId: number;
  /** 0 Shot → 1 Editing → 2 Proofing → 3 Delivered */
  stage: number;
  /** Where the client views their photos; set when delivered. */
  link: string | null;
  deliveredAt: string | null;
}

export interface DeliverRequest {
  link: string;
  /** What the client actually paid; null keeps the current quote. */
  finalPrice: number | null;
  sendEmail: boolean;
  subject: string;
  body: string;
}

/** A published photo as the public site renders it. */
export interface PhotoSrc {
  src: string;
  /** Responsive candidates ("…-480.jpg 480w, …"); null for photos uploaded before renditions existed. */
  srcset: string | null;
  width: number | null;
}

export interface PhotoSlotView {
  id: string;
  label: string;
  url: string | null;
}

export interface PhotoGroup {
  title: string;
  slots: PhotoSlotView[];
}

export type EmailAction = 'approve' | 'decline' | 'cancel' | 'message';

export type MoveReason = 'CLIENT_REQUEST' | 'WEATHER' | 'STUDIO' | 'OTHER';

export interface RescheduleRequest {
  date: string;
  /** "HH:mm" */
  time: string;
  reason: MoveReason;
  sendEmail: boolean;
  subject: string;
  body: string;
}

/** Draft vs. live: which kinds of site content have unpublished changes. */
export interface PublishStatus {
  dirty: boolean;
  changes: { pricing: boolean; events: boolean; photos: boolean };
  publishedAt: string | null;
}

export interface EmailDraft {
  subject: string;
  body: string;
  to: string;
}

export interface EmailRequest {
  sendEmail?: boolean;
  subject?: string;
  body?: string;
}

export type Access = 'OWNER' | 'ADMIN' | 'EMPLOYEE';

/** The signed-in person. `role` is the job title; `access` decides which sections they see. */
export interface AdminUser {
  name: string;
  role: string;
  initial: string;
  access: Access;
  mustChangePassword: boolean;
}

/** A team member as the Team page shows it. */
export interface TeamMember {
  name: string;
  role: string;
  email: string | null;
  access: Access;
  active: boolean;
  mustChangePassword: boolean;
  createdAt: string | null;
  isYou: boolean;
  /** Offered on the public booking page as a photographer to choose. */
  bookable: boolean;
}

/** A photographer customers may pick on the booking page. */
export interface Photographer {
  name: string;
  role: string;
}

export interface LoginResponse {
  token: string;
  expiresInSeconds: number;
  user: AdminUser;
}
